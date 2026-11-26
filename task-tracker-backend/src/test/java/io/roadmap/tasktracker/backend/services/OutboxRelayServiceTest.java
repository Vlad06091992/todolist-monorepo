package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.dto.UserRegisteredEvent;
import io.roadmap.tasktracker.backend.entities.outbox.EventOutbox;
import io.roadmap.tasktracker.backend.entities.outbox.OutboxEventType;
import io.roadmap.tasktracker.backend.kafka.KafkaEventPublisher;
import io.roadmap.tasktracker.backend.repositories.EventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.roadmap.tasktracker.backend.entities.outbox.OutboxEventType.USER_DELETED;
import static io.roadmap.tasktracker.backend.entities.outbox.OutboxEventType.USER_REGISTERED;
import static io.roadmap.tasktracker.backend.entities.outbox.OutboxStatus.FAILED;
import static io.roadmap.tasktracker.backend.entities.outbox.OutboxStatus.SENT;
import static io.roadmap.tasktracker.backend.entities.outbox.OutboxStatus.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link OutboxRelayService} — no Spring context, no database,
 * no Kafka, no sleeps.
 *
 * <p>Behaviors pinned from the current source code:</p>
 * <ul>
 *   <li><b>Constructor</b> (Lombok {@code @RequiredArgsConstructor} over the final fields,
 *       in declaration order): {@code (EventOutboxRepository, KafkaEventPublisher,
 *       Scheduler dbScheduler, ObjectMapper)}. The topic is the <i>non-final</i>,
 *       field-injected {@code @Value} field {@code notificationsEmailRequested} — it is
 *       NOT a constructor argument and is set here via {@link ReflectionTestUtils}.</li>
 *   <li><b>Fire-and-forget</b>: each row runs through
 *       {@code sendMessage(...).then(...).onErrorResume(...).subscribeOn(dbScheduler).subscribe(...)}.
 *       Injecting {@link Schedulers#immediate()} makes the whole chain run synchronously
 *       on the calling thread, so Mockito {@code verify()} right after
 *       {@code dispatchPendingOutboxEvents()} is deterministic. The service method is
 *       {@code void} and returns no reactive values, so no {@code StepVerifier} is needed
 *       (reactive steps are verified via the mocked publisher/repository instead).</li>
 *   <li><b>Success</b>: status=SENT, attempts incremented by 1, row saved.</li>
 *   <li><b>Error</b>: attempts incremented by 1, lastError={@code formatError(e)}; the row
 *       becomes FAILED only when the NEW attempts value is {@code >= 3}. A row already at
 *       3 attempts that fails again is saved with attempts=4 — attempts may exceed the cap,
 *       there is no upper clamp.</li>
 *   <li><b>formatError</b>: walks {@code getCause()} down to the root (guarded against
 *       self-cause), renders {@code "SimpleName: message"}; a null/blank message renders
 *       the bare simple name; a message longer than 500 chars is truncated to its first
 *       500 chars plus a literal {@code "..."} suffix.</li>
 *   <li><b>Default switch branch</b> (anything but USER_REGISTERED, e.g. USER_DELETED):
 *       saved as FAILED immediately; attempts NOT incremented, lastError NOT set,
 *       publisher never called.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    /** Value mirrored into the @Value field {@code notificationsEmailRequested}. */
    private static final String TOPIC = "notifications-email-requested";

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        // Constructor parameter order is fixed by Lombok @RequiredArgsConstructor:
        // (eventOutboxRepository, kafkaEventPublisher, dbScheduler, objectMapper).
        // Schedulers.immediate() forces every fire-and-forget chain to complete
        // synchronously on the test thread.
        outboxRelayService = new OutboxRelayService(
                eventOutboxRepository,
                kafkaEventPublisher,
                Schedulers.immediate(),
                new ObjectMapper()
        );
        // Field-injected @Value never runs outside a Spring container -> set reflectively.
        ReflectionTestUtils.setField(outboxRelayService, "notificationsEmailRequested", TOPIC);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Builds an outbox row the way production does: UserService stores the payload via
     * {@code objectMapper.convertValue(message, Map.class)}, so the map keys are exactly
     * the {@link UserRegisteredEvent} record components
     * (login, email, greetingText, eventId). All four keys are supplied so Jackson 3
     * record deserialization cannot trip over missing creator properties.
     */
    private static EventOutbox pendingRow(OutboxEventType type, int attempts) {
        EventOutbox row = new EventOutbox();
        row.setId(UUID.randomUUID());
        row.setType(type);
        row.setStatus(WAITING);
        row.setAttempts(attempts);
        row.setPayload(Map.of(
                "login", "alice",
                "email", "alice@example.com",
                "greetingText", "Hello alice !",
                "eventId", "e-123"
        ));
        return row;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. Success: WAITING USER_REGISTERED row is published on the topic and saved as SENT with attempts=1 and no lastError")
    void successMarksRowSentAndIncrementsAttempts() {
        EventOutbox row = pendingRow(USER_REGISTERED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.empty());

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        EventOutbox persisted = saved.getValue();
        assertThat(persisted.getStatus()).isEqualTo(SENT);
        assertThat(persisted.getAttempts()).isEqualTo(1);
        assertThat(persisted.getLastError()).isNull();

        // The deserialized event must reach the publisher on the configured topic.
        ArgumentCaptor<UserRegisteredEvent> published = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(kafkaEventPublisher).sendMessage(eq(TOPIC), published.capture());
        assertThat(published.getValue().login()).isEqualTo("alice");
        assertThat(published.getValue().email()).isEqualTo("alice@example.com");
        assertThat(published.getValue().greetingText()).isEqualTo("Hello alice !");
        assertThat(published.getValue().eventId()).isEqualTo("e-123");
    }

    @Test
    @DisplayName("2. Failure below cap: attempts=0 + send error -> saved back as WAITING with attempts=1 and lastError recorded")
    void failureBelowCapKeepsRowWaitingForRetry() {
        EventOutbox row = pendingRow(USER_REGISTERED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.<Void>error(new RuntimeException("kafka down")));

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        EventOutbox persisted = saved.getValue();
        assertThat(persisted.getStatus()).isEqualTo(WAITING); // stays eligible for retry
        assertThat(persisted.getAttempts()).isEqualTo(1);
        assertThat(persisted.getLastError())
                .contains("RuntimeException")
                .contains("kafka down");
    }

    @Test
    @DisplayName("3. Cap reached: attempts=2 + send error -> newAttempts=3 >= 3 -> saved as FAILED with attempts=3")
    void failureAtCapMarksRowFailed() {
        EventOutbox row = pendingRow(USER_REGISTERED, 2);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.<Void>error(new RuntimeException("kafka down")));

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        EventOutbox persisted = saved.getValue();
        assertThat(persisted.getStatus()).isEqualTo(FAILED);
        assertThat(persisted.getAttempts()).isEqualTo(3);
        assertThat(persisted.getLastError()).contains("kafka down");
    }

    @Test
    @DisplayName("4. Boundary pin: attempts=3 (already at cap) + send error -> FAILED with attempts=4; attempts may exceed the cap")
    void failureBeyondCapIncrementsAttemptsPastCap() {
        // Actual source behavior: attempts is incremented BEFORE the >= 3 check and there
        // is no upper clamp, so a row that keeps failing on retries beyond the cap keeps
        // counting up (3 -> 4 here) while being marked FAILED.
        EventOutbox row = pendingRow(USER_REGISTERED, 3);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.<Void>error(new RuntimeException("kafka down")));

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        EventOutbox persisted = saved.getValue();
        assertThat(persisted.getStatus()).isEqualTo(FAILED);
        assertThat(persisted.getAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("5. No pending rows: publisher untouched and nothing saved")
    void noPendingRowsSkipsEverything() {
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of());

        outboxRelayService.dispatchPendingOutboxEvents();

        verify(eventOutboxRepository).findByStatus(WAITING);
        verifyNoInteractions(kafkaEventPublisher);
        verify(eventOutboxRepository, never()).save(any(EventOutbox.class));
    }

    @Test
    @DisplayName("6. Unknown type (USER_DELETED): never published, saved as FAILED immediately with attempts and lastError untouched")
    void unknownEventTypeFailsImmediatelyWithoutPublishing() {
        // Documents the missing case branch: USER_DELETED falls into the default branch.
        EventOutbox row = pendingRow(USER_DELETED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));

        outboxRelayService.dispatchPendingOutboxEvents();

        verifyNoInteractions(kafkaEventPublisher);

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        EventOutbox persisted = saved.getValue();
        assertThat(persisted.getStatus()).isEqualTo(FAILED);
        assertThat(persisted.getAttempts()).isZero();   // default branch does not count attempts
        assertThat(persisted.getLastError()).isNull();  // ... and does not record a reason
    }

    @Test
    @DisplayName("7. formatError truncation: 600-char root-cause message is stored as first 500 chars + literal '...'")
    void longRootCauseMessageIsTruncatedTo500CharsWithEllipsis() {
        String longMessage = "kafka-blowup-".repeat(50); // 650 chars > 500

        EventOutbox row = pendingRow(USER_REGISTERED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.<Void>error(new RuntimeException(longMessage)));

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        // Exact source semantics: "SimpleName: " + message.substring(0, 500) + "..."
        String expectedLastError = "RuntimeException: " + longMessage.substring(0, 500) + "...";
        assertThat(saved.getValue().getLastError()).isEqualTo(expectedLastError);
    }

    @Test
    @DisplayName("8. formatError unwraps to the root cause: nested IllegalStateException reported, outer wrapper dropped")
    void lastErrorReportsRootCauseNotWrapper() {
        EventOutbox row = pendingRow(USER_REGISTERED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(row));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.<Void>error(
                        new RuntimeException("outer", new IllegalStateException("root cause msg"))));

        outboxRelayService.dispatchPendingOutboxEvents();

        ArgumentCaptor<EventOutbox> saved = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepository).save(saved.capture());

        assertThat(saved.getValue().getLastError())
                .isEqualTo("IllegalStateException: root cause msg")
                .doesNotContain("outer");
    }

    @Test
    @DisplayName("9. Multiple WAITING rows: every row is published and saved; with the immediate scheduler both chains complete synchronously")
    void multipleRowsAreAllProcessedSynchronously() {
        EventOutbox first = pendingRow(USER_REGISTERED, 0);
        EventOutbox second = pendingRow(USER_REGISTERED, 0);
        when(eventOutboxRepository.findByStatus(WAITING)).thenReturn(List.of(first, second));
        when(kafkaEventPublisher.sendMessage(anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(Mono.empty());

        outboxRelayService.dispatchPendingOutboxEvents();

        verify(kafkaEventPublisher, times(2)).sendMessage(eq(TOPIC), any(UserRegisteredEvent.class));
        verify(eventOutboxRepository, times(2)).save(any(EventOutbox.class));

        // Both fire-and-forget chains have already completed on this thread.
        assertThat(first.getStatus()).isEqualTo(SENT);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo(SENT);
        assertThat(second.getAttempts()).isEqualTo(1);
    }
}
