package io.roadmap.tasktracker.backend.kafka;

import org.apache.kafka.clients.producer.RecordMetadata;
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

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link KafkaEventPublisher} (no Spring context, no Kafka broker).
 *
 * <p>Pins the delivery-failure contract: a per-record failure arrives from reactor-kafka as a
 * normal {@code onNext} carrying {@code SenderResult.exception() != null} — the publisher must
 * convert it into a stream error so downstream consumers (the outbox relay) can retry instead of
 * marking the event SENT. A stream-level error from {@code sender.send()} propagates unchanged.
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    private static final String TOPIC = "test-topic";

    @Mock
    private KafkaSender<Integer, Object> sender;

    @Captor
    private ArgumentCaptor<Publisher<SenderRecord<Integer, Object, Integer>>> recordsCaptor;

    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(sender);
    }

    @Test
    @DisplayName("sendMessage: per-record delivery failure (SenderResult with exception) errors the Mono with the original exception")
    void sendMessage_failedSenderResult_errorsMono() {
        // Documents the fix: without the flatMap conversion, .then() completed successfully and
        // the outbox relay marked the event SENT despite the failed delivery.
        TimeoutException failure = new TimeoutException("broker down");
        // Hoisted BEFORE the when() call: Mockito forbids nested stubbing of another mock
        // while the outer when(...).thenReturn(...) chain is still unfinished.
        SenderResult<Integer> result = failedResult(failure);
        when(sender.send(ArgumentMatchers.<Publisher<SenderRecord<Integer, Object, Integer>>>any()))
                .thenReturn(Flux.just(result));

        StepVerifier.create(publisher.sendMessage(TOPIC, "payload"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(TimeoutException.class)
                        .isSameAs(failure)
                        .hasMessage("broker down"))
                .verify();
    }

    @Test
    @DisplayName("sendMessage: successful SenderResult completes the Mono empty")
    void sendMessage_successfulSenderResult_completesEmpty() {
        SenderResult<Integer> result = successResult(metadata());
        when(sender.send(ArgumentMatchers.<Publisher<SenderRecord<Integer, Object, Integer>>>any()))
                .thenReturn(Flux.just(result));

        StepVerifier.create(publisher.sendMessage(TOPIC, "payload"))
                .verifyComplete();
    }

    @Test
    @DisplayName("sendMessage: stream-level error from sender.send propagates to the caller unchanged")
    void sendMessage_streamError_propagates() {
        when(sender.send(ArgumentMatchers.<Publisher<SenderRecord<Integer, Object, Integer>>>any()))
                .thenReturn(Flux.error(new IllegalStateException("sender closed")));

        StepVerifier.create(publisher.sendMessage(TOPIC, "payload"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("sender closed"))
                .verify();
    }

    @Test
    @DisplayName("sendMessage: builds a SenderRecord with the given topic, payload, null key and null correlation metadata")
    void sendMessage_buildsRecordCorrectly() {
        SenderResult<Integer> result = successResult(metadata());
        when(sender.send(ArgumentMatchers.<Publisher<SenderRecord<Integer, Object, Integer>>>any()))
                .thenReturn(Flux.just(result));

        StepVerifier.create(publisher.sendMessage(TOPIC, "payload"))
                .verifyComplete();

        verify(sender).send(recordsCaptor.capture());

        // The captured value is the Publisher (a Mono) of the single SenderRecord — subscribe to inspect it.
        StepVerifier.create(recordsCaptor.getValue())
                .assertNext(record -> {
                    assertThat(record.topic()).isEqualTo(TOPIC);
                    assertThat(record.value()).isEqualTo("payload");
                    assertThat(record.key()).isNull();
                    assertThat(record.correlationMetadata()).isNull();
                })
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // Fixtures (mocks of reactor-kafka interfaces; no real Kafka involved)
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static SenderResult<Integer> failedResult(Exception exception) {
        SenderResult<Integer> result = mock(SenderResult.class);
        when(result.exception()).thenReturn(exception);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static SenderResult<Integer> successResult(RecordMetadata recordMetadata) {
        SenderResult<Integer> result = mock(SenderResult.class);
        when(result.exception()).thenReturn(null);
        when(result.recordMetadata()).thenReturn(recordMetadata);
        return result;
    }

    private static RecordMetadata metadata() {
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(recordMetadata.topic()).thenReturn(TOPIC);
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(42L);
        return recordMetadata;
    }
}
