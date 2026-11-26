package io.roadmap.tasktracker.scheduler.report;

import io.roadmap.tasktracker.scheduler.client.BackendTaskClient;
import io.roadmap.tasktracker.scheduler.kafka.KafkaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ReportGenerationService} — no Spring context, no
 * Kafka broker, no backend HTTP. Only the request/reply composition of
 * {@code generateReportsForPage(date, page, pageSize)} is tested;
 * {@code startReplyListener} (which would build a real {@code KafkaReceiver})
 * is out of scope — the reply is simulated by emitting into the internal
 * {@code pending} sink directly.
 * <p>
 * Determinism notes:
 * <ul>
 *   <li>a fresh service is created per test because {@code pending} and
 *       {@code emailsByLogin} are instance state;</li>
 *   <li>both collaborators are Mockito mocks with immediate values, so the
 *       request phase settles synchronously during {@code subscribe()};</li>
 *   <li>all latches are bounded (500 ms for "must NOT complete", 2 s for
 *       "must complete") — no open-ended waits.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportGenerationService request/reply round trip (mocked collaborators)")
class ReportGenerationServiceTest {

    private static final String DATE = "2026-11-26";
    private static final int PAGE = 0;
    private static final int PAGE_SIZE = 50;

    private static final String SUMMARIES_REQUESTED_TOPIC = "req-topic";
    private static final String SUMMARIES_COMPLETED_TOPIC = "done-topic";
    private static final String EMAIL_REPORT_TOPIC = "email-topic";

    @Mock
    private KafkaService kafkaService;

    @Mock
    private ReceiverOptions<String, Object> receiverOptions;

    @Mock
    private BackendTaskClient backendTaskClient;

    @Captor
    private ArgumentCaptor<List<UserSummary>> requestPayloadCaptor;

    @Captor
    private ArgumentCaptor<EmailReportRequestedEvent> emailEventCaptor;

    @Captor
    private ArgumentCaptor<String> correlationIdCaptor;

    private ReportGenerationService service;

    @BeforeEach
    void setUp() {
        // Lombok @RequiredArgsConstructor ctor order mirrors the field declaration
        // order in ReportGenerationService: (KafkaService, ReceiverOptions, BackendTaskClient).
        service = new ReportGenerationService(kafkaService, receiverOptions, backendTaskClient);

        ReflectionTestUtils.setField(service, "summariesRequestedTopic", SUMMARIES_REQUESTED_TOPIC);
        ReflectionTestUtils.setField(service, "summariesCompletedTopic", SUMMARIES_COMPLETED_TOPIC);
        ReflectionTestUtils.setField(service, "emailReportRequestedTopic", EMAIL_REPORT_TOPIC);
    }

    @Test
    @DisplayName("request phase: summaries are published to the request topic with a correlation id and the mono stays pending")
    void requestPhase_publishesSummaries_andStaysPending() throws Exception {
        when(backendTaskClient.fetchUsersWithFinishedTasks(anyString(), anyInt(), anyInt()))
                .thenReturn(Flux.just(userSummary("alice", "alice@x.com"), userSummary("bob", "bob@x.com")));
        when(kafkaService.sendMessage(eq(SUMMARIES_REQUESTED_TOPIC), any(), anyString()))
                .thenReturn(Mono.empty());

        CountDownLatch terminated = new CountDownLatch(1);
        Disposable subscription = service.generateReportsForPage(DATE, PAGE, PAGE_SIZE)
                .doFinally(signal -> terminated.countDown())
                .subscribe();

        // Everything up to the reply sink is synchronous; the mono must still be
        // waiting for the correlation-id reply 500 ms later.
        boolean finishedEarly = terminated.await(500, TimeUnit.MILLISECONDS);
        subscription.dispose();

        assertThat(finishedEarly)
                .as("the mono must stay pending until the correlation-id reply arrives")
                .isFalse();

        verify(kafkaService).sendMessage(
                eq(SUMMARIES_REQUESTED_TOPIC), requestPayloadCaptor.capture(), correlationIdCaptor.capture());

        List<UserSummary> sentSummaries = requestPayloadCaptor.getValue();
        assertThat(sentSummaries)
                .as("user summaries must be forwarded preserving the backend order")
                .extracting(summary -> summary.login)
                .containsExactly("alice", "bob");
        assertThat(correlationIdCaptor.getValue())
                .as("a non-blank correlation id must be generated for the request")
                .isNotBlank();
    }

    @Test
    @DisplayName("round trip: reply JSON is parsed and each event is enriched with the fetched summary email")
    void roundTrip_replyIsParsedAndEnrichedWithEmail() throws Exception {
        when(backendTaskClient.fetchUsersWithFinishedTasks(anyString(), anyInt(), anyInt()))
                .thenReturn(Flux.just(userSummary("alice", "alice@x.com")));
        when(kafkaService.sendMessage(eq(SUMMARIES_REQUESTED_TOPIC), any(), anyString()))
                .thenReturn(Mono.empty());
        when(kafkaService.sendMessage(eq(EMAIL_REPORT_TOPIC), any(), anyString()))
                .thenReturn(Mono.empty());

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        service.generateReportsForPage(DATE, PAGE, PAGE_SIZE)
                .subscribe(value -> { }, errorRef::set, completed::countDown);

        // The request phase already ran synchronously; capture the generated correlation id.
        verify(kafkaService).sendMessage(
                eq(SUMMARIES_REQUESTED_TOPIC), requestPayloadCaptor.capture(), correlationIdCaptor.capture());
        String correlationId = correlationIdCaptor.getValue();
        assertThat(correlationId).isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Sinks.One<String>> pending =
                (Map<String, Sinks.One<String>>) ReflectionTestUtils.getField(service, "pending");
        Sinks.One<String> replySink = pending.get(correlationId);
        assertThat(replySink)
                .as("a pending sink must be registered for correlation id %s", correlationId)
                .isNotNull();

        // Simulate the reply listener resolving the pending sink.
        Sinks.EmitResult emitResult =
                replySink.tryEmitValue("[{\"login\":\"alice\",\"message\":\"Great job\",\"email\":null}]");
        assertThat(emitResult).as("the reply must be accepted by the pending sink").isEqualTo(Sinks.EmitResult.OK);

        assertThat(completed.await(2, TimeUnit.SECONDS))
                .as("the mono should complete after the reply arrives")
                .isTrue();
        assertThat(errorRef.get()).as("no unexpected error during the round trip").isNull();

        verify(kafkaService).sendMessage(eq(EMAIL_REPORT_TOPIC), emailEventCaptor.capture(), anyString());
        EmailReportRequestedEvent enriched = emailEventCaptor.getValue();
        assertThat(enriched.login()).isEqualTo("alice");
        assertThat(enriched.message()).isEqualTo("Great job");
        assertThat(enriched.email())
                .as("the event must be enriched with the email collected from the summaries")
                .isEqualTo("alice@x.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("garbage reply: unparsable payload errors the mono and no email is published")
    void roundTrip_garbageReply_errorsMonoAndSendsNoEmails() throws Exception {
        when(backendTaskClient.fetchUsersWithFinishedTasks(anyString(), anyInt(), anyInt()))
                .thenReturn(Flux.just(userSummary("alice", "alice@x.com")));
        when(kafkaService.sendMessage(eq(SUMMARIES_REQUESTED_TOPIC), any(), anyString()))
                .thenReturn(Mono.empty());

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch failed = new CountDownLatch(1);
        service.generateReportsForPage(DATE, PAGE, PAGE_SIZE)
                .doOnError(e -> {
                    errorRef.set(e);
                    failed.countDown();
                })
                .subscribe(value -> { }, error -> { });

        verify(kafkaService).sendMessage(eq(SUMMARIES_REQUESTED_TOPIC), any(), correlationIdCaptor.capture());
        String correlationId = correlationIdCaptor.getValue();

        Map<String, Sinks.One<String>> pending =
                (Map<String, Sinks.One<String>>) ReflectionTestUtils.getField(service, "pending");
        Sinks.One<String> replySink = pending.get(correlationId);
        assertThat(replySink)
                .as("a pending sink must be registered for correlation id %s", correlationId)
                .isNotNull();

        replySink.tryEmitValue("not-json");

        assertThat(failed.await(2, TimeUnit.SECONDS))
                .as("the mono should fail on the unparsable reply")
                .isTrue();
        assertThat(errorRef.get())
                .as("Jackson 3 (tools.jackson) reports parse failures as unchecked exceptions")
                .isInstanceOf(RuntimeException.class);

        // Exactly one sendMessage call overall — the request itself. The email publish never happened.
        verify(kafkaService, times(1)).sendMessage(eq(SUMMARIES_REQUESTED_TOPIC), any(), anyString());
        verify(kafkaService, times(1)).sendMessage(anyString(), any(), anyString());
    }

    private static UserSummary userSummary(String login, String email) {
        return new UserSummary(UUID.randomUUID(), login, email, List.of("task-1", "task-2"));
    }
}
