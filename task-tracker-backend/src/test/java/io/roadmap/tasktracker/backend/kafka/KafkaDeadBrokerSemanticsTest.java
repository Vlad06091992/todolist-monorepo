package io.roadmap.tasktracker.backend.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-flavored semantics test: a REAL kafka-clients producer (no mocks) pointed at a
 * dead bootstrap server — an unassigned local port, i.e. instant connection-refused. This is
 * the deterministic equivalent of "Kafka container killed BEFORE the producer ever reached it"
 * (no topic metadata cached).
 *
 * <p>Empirical result (this test pins it): with no cached metadata, {@code producer.send()}
 * throws {@code TimeoutException} synchronously and reactor-kafka surfaces it as a STREAM error —
 * the {@code doOnNext} per-record path is never invoked. Therefore even the PRE-FIX publisher
 * chain errored on this failure class, and the outbox relay retried it properly. The false-SENT
 * bug lives in a different failure class: callback-based per-record delivery failures
 * (metadata already cached + delivery timeout on a live connection), covered by
 * {@link KafkaEventPublisherTest} with a mocked sender.
 *
 * <p>Timeouts are intentionally small (300/100/300 ms) so both tests run in ~1s; the signal
 * semantics (stream error vs per-record result) do not depend on the timeout values.
 */
class KafkaDeadBrokerSemanticsTest {

    private static final String DEAD_BOOTSTRAP = deadBootstrap();
    private static final String TOPIC = "any-topic";

    private KafkaSender<Integer, Object> sender;

    /**
     * Grabs a free port and releases it — nothing is listening there, so every connect
     * attempt gets an immediate connection-refused (the "killed container" case).
     */
    private static String deadBootstrap() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "localhost:" + socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException("Could not reserve a free port", e);
        }
    }

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, DEAD_BOOTSTRAP);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 1);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 300);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 100);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 300);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        sender = KafkaSender.create(SenderOptions.create(props));
    }

    @AfterEach
    void tearDown() {
        sender.close();
    }

    private Flux<SenderResult<Integer>> sendOne() {
        return sender.send(Mono.just(SenderRecord.create(new ProducerRecord<>(TOPIC, "payload"), null)));
    }

    @Test
    @DisplayName("DEAD BROKER (no metadata): even the PRE-FIX chain errors with TimeoutException — the relay's retry path worked for this failure class")
    void deadBroker_streamErrorEvenInPreFixChain() {
        AtomicReference<Throwable> perRecordException = new AtomicReference<>();

        Mono<Void> preFixChain = sendOne()
                .doOnNext(result -> perRecordException.set(result.exception()))
                .then();

        StepVerifier.create(preFixChain)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(org.apache.kafka.common.errors.TimeoutException.class)
                        .hasMessageContaining("not present in metadata"))
                .verify();

        // The error bypassed the per-record path entirely — doOnNext never ran,
        // so the pre-fix code had nothing to "swallow" in this scenario.
        assertThat(perRecordException.get()).isNull();
    }

    @Test
    @DisplayName("DEAD BROKER (no metadata): the FIXED chain also errors — the fix does not change behavior for this failure class")
    void deadBroker_fixedChainErrorsAsWell() {
        AtomicReference<Throwable> perRecordException = new AtomicReference<>();

        Mono<Void> fixedChain = sendOne()
                .doOnNext(result -> perRecordException.set(result.exception()))
                .flatMap(result -> result.exception() != null
                        ? Mono.error(result.exception())
                        : Mono.<Void>empty())
                .then();

        StepVerifier.create(fixedChain)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(org.apache.kafka.common.errors.TimeoutException.class)
                        .hasMessageContaining("not present in metadata"))
                .verify();

        assertThat(perRecordException.get()).isNull();
    }
}
