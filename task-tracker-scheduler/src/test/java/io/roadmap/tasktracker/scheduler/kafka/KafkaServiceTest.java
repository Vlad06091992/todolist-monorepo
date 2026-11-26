package io.roadmap.tasktracker.scheduler.kafka;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link KafkaService} — no Spring context, no Kafka broker.
 * <p>
 * The {@link KafkaSender} collaborator is a Mockito mock; the tests verify the
 * composition logic only: the outgoing {@link SenderRecord} shape (topic, value,
 * null key, null correlation metadata, CORRELATION_ID header) and the fact that
 * the returned {@code Mono<Void>} reflects the sender's outcome.
 * <p>
 * IMPORTANT BEHAVIOR PIN: per-record delivery failures reported through
 * {@link SenderResult#exception()} are NOT converted into reactive errors —
 * {@code sendMessage} logs every {@link SenderResult} as "delivered" and
 * completes normally. This is the same defect as the backend's
 * {@code KafkaEventPublisher} pre-fix (documented in
 * task-tracker-backend/docs/kafka-delivery-failure-cases.md, class B).
 * These tests deliberately PIN that current behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaService (mocked KafkaSender, no broker)")
class KafkaServiceTest {

    private static final String TOPIC = "test-topic";
    private static final String MESSAGE = "payload";
    private static final String CORRELATION_ID = "corr-123";

    @Mock
    private KafkaSender<Integer, Object> sender;

    @Captor
    private ArgumentCaptor<Publisher<SenderRecord<Integer, Object, Integer>>> recordPublisherCaptor;

    private KafkaService service;

    @BeforeEach
    void setUp() {
        service = new KafkaService(sender);
    }

    @Test
    @DisplayName("sendMessage completes empty when the sender reports a successful delivery")
    void sendMessage_completesEmptyOnSuccessfulDelivery() {
        // Build the SenderResult mock BEFORE stubbing sender.send — Mockito forbids
        // creating/stubbing a mock while another when(...) stubbing is in progress.
        SenderResult<Integer> successResult = successSenderResult(TOPIC, 0, 0L);
        // Consume the helper's stubs explicitly (strict stubs): a success result has
        // no exception and carries record metadata.
        assertThat(successResult.exception()).isNull();
        assertThat(successResult.recordMetadata().topic()).isEqualTo(TOPIC);
        assertThat(successResult.recordMetadata().partition()).isZero();
        assertThat(successResult.recordMetadata().offset()).isZero();

        when(sender.send(anySenderRecordPublisher())).thenReturn(Flux.just(successResult));

        StepVerifier.create(service.sendMessage(TOPIC, MESSAGE, CORRELATION_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("per-record SenderResult failure is swallowed: the Mono still completes empty (pinned defect, backend class B)")
    void sendMessage_swallowsPerRecordDeliveryFailure() {
        // Same defect as the backend's KafkaEventPublisher pre-fix
        // (task-tracker-backend/docs/kafka-delivery-failure-cases.md, class B):
        // a SenderResult carrying a non-null exception() is never converted into a
        // reactive error — it is logged as "delivered" like every other record.
        Exception deliveryFailure = new IllegalStateException("simulated broker-side delivery failure");
        SenderResult<Integer> failedResult = failedSenderResult(deliveryFailure);
        // Consume the helper's stub explicitly (strict stubs): the failed result
        // reports the failure through exception().
        assertThat(failedResult.exception()).isSameAs(deliveryFailure);

        when(sender.send(anySenderRecordPublisher())).thenReturn(Flux.just(failedResult));

        // PIN: despite the failed SenderResult, the Mono completes empty.
        // Whether doOnNext logged the failed record as "delivered" is out of scope
        // here — only the (defective) completion semantics are asserted.
        StepVerifier.create(service.sendMessage(TOPIC, MESSAGE, CORRELATION_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("outgoing SenderRecord carries exact topic/value, null key, null correlation metadata and the CORRELATION_ID header")
    void sendMessage_buildsSenderRecordWithExpectedContents() {
        when(sender.send(recordPublisherCaptor.capture())).thenReturn(Flux.empty());

        StepVerifier.create(service.sendMessage(TOPIC, MESSAGE, CORRELATION_ID))
                .verifyComplete();

        // The captured publisher is the (cold) Mono built by buildRecord — the mock
        // never subscribed to it, so it can be subscribed and inspected directly.
        StepVerifier.create(recordPublisherCaptor.getValue())
                .assertNext(record -> {
                    assertThat(record.topic()).isEqualTo(TOPIC);
                    assertThat(record.value()).isEqualTo(MESSAGE);
                    assertThat(record.key()).as("no key is passed to the ProducerRecord").isNull();
                    assertThat(record.correlationMetadata())
                            .as("SenderRecord.create(producerRecord, null) — correlation metadata is null")
                            .isNull();

                    Header header = record.headers().lastHeader(KafkaService.CORRELATION_ID_HEADER);
                    assertThat(header).as("CORRELATION_ID header must be present").isNotNull();
                    assertThat(header.value())
                            .as("header value must be the correlationId as UTF-8 bytes")
                            .isEqualTo(CORRELATION_ID.getBytes(StandardCharsets.UTF_8));
                    assertThat(new String(header.value(), StandardCharsets.UTF_8))
                            .isEqualTo(CORRELATION_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("null correlationId fails synchronously with NullPointerException from the sendMessage call itself")
    void sendMessage_nullCorrelationId_throwsSynchronously() {
        // buildRecord eagerly evaluates correlationId.getBytes(UTF_8) when sendMessage
        // is invoked — i.e. before subscription and before sender.send is reached.
        assertThatThrownBy(() -> service.sendMessage("t", "m", null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(sender);
    }

    /**
     * Typed matcher resolving the generic {@code send(Publisher<SenderRecord<K, V, C>>)}
     * overload (KafkaService uses that one; a raw any() would be ambiguous with the
     * ProducerRecord overload).
     */
    private static Publisher<SenderRecord<Integer, Object, Integer>> anySenderRecordPublisher() {
        return ArgumentMatchers.any();
    }

    @SuppressWarnings("unchecked")
    private static SenderResult<Integer> successSenderResult(String topic, int partition, long offset) {
        SenderResult<Integer> result = mock(SenderResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn(topic);
        when(metadata.partition()).thenReturn(partition);
        when(metadata.offset()).thenReturn(offset);
        when(result.recordMetadata()).thenReturn(metadata);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static SenderResult<Integer> failedSenderResult(Exception failure) {
        SenderResult<Integer> result = mock(SenderResult.class);
        when(result.exception()).thenReturn(failure);
        return result;
    }
}
