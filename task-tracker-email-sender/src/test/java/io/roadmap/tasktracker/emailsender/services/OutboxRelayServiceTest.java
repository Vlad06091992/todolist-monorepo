package io.roadmap.tasktracker.emailsender.services;

import io.roadmap.tasktracker.emailsender.entities.EventOutbox;
import io.roadmap.tasktracker.emailsender.entities.OutboxEventType;
import io.roadmap.tasktracker.emailsender.entities.OutboxStatus;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.repositories.EventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link OutboxRelayService}: mock-based, no Spring context, no infrastructure.
 *
 * <p>The test lives in the same package as the service because {@code relayPendingEvents()} is
 * {@code protected} — same-package placement is the only way to invoke it without a Spring proxy.
 *
 * <p>{@code relayPendingEvents()} is fully synchronous: it loads WAITING rows from the outbox
 * repository and, for {@code SEND_EMAIL} rows, converts the JSON payload into a
 * {@link UserRegisteredEvent} via a static Jackson 2 {@code ObjectMapper}, delegates to
 * {@link EmailService#sendGreetingEmail(UserRegisteredEvent)}, and persists the row state
 * (SENT on success; on failure the attempt counter is incremented first and the row becomes
 * FAILED only once attempts reach 3 — the cap is checked after the increment, so it can be exceeded).
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    /** Arbitrary but fixed UUID string used as the payload's eventId. */
    private static final String EVENT_ID = "1b0f2b5e-6c1d-4a4e-9a3f-0a6c5d7e8f90";

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    @Captor
    private ArgumentCaptor<UserRegisteredEvent> sentEventCaptor;

    @Captor
    private ArgumentCaptor<EventOutbox> savedRowCaptor;

    @Test
    @DisplayName("SEND_EMAIL success: payload converted to UserRegisteredEvent, email sent, row saved as SENT with attempts incremented and no error")
    void relayPendingEventsSuccessPathSendsConvertedEventAndMarksRowSent() {
        EventOutbox row = waitingRow(0, Map.of(
                "login", "alice",
                "email", "a@x.com",
                "greetingText", "Hello alice !",
                "eventId", EVENT_ID));
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();

        outboxRelayService.relayPendingEvents();

        // The payload map must be converted into the record before the email is sent.
        verify(emailService).sendGreetingEmail(sentEventCaptor.capture());
        UserRegisteredEvent sent = sentEventCaptor.getValue();
        assertThat(sent.login()).isEqualTo("alice");
        assertThat(sent.email()).isEqualTo("a@x.com");
        assertThat(sent.greetingText()).isEqualTo("Hello alice !");
        assertThat(sent.eventId()).isEqualTo(EVENT_ID);

        // The row must be persisted as SENT with exactly one new attempt and no error recorded.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        EventOutbox saved = savedRowCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    @DisplayName("SEND_EMAIL failure at attempts=0: row stays WAITING, attempts becomes 1, lastError is 'RuntimeException: smtp down'")
    void relayPendingEventsFirstFailureKeepsRowWaitingWithFormattedLastError() {
        EventOutbox row = waitingRow(0, defaultPayload());
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendGreetingEmail(any(UserRegisteredEvent.class));

        outboxRelayService.relayPendingEvents();

        // attempts=1 < 3, so the row must NOT be marked FAILED yet.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        EventOutbox saved = savedRowCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.WAITING);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("RuntimeException: smtp down");
    }

    @Test
    @DisplayName("SEND_EMAIL failure at attempts=2: third attempt reaches the cap, row saved as FAILED with attempts=3")
    void relayPendingEventsFailureAtSecondAttemptMarksRowFailed() {
        EventOutbox row = waitingRow(2, defaultPayload());
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendGreetingEmail(any(UserRegisteredEvent.class));

        outboxRelayService.relayPendingEvents();

        // The cap check happens after the increment: attempts 2 -> 3 >= 3 => FAILED.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        EventOutbox saved = savedRowCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(saved.getAttempts()).isEqualTo(3);
        assertThat(saved.getLastError()).isEqualTo("RuntimeException: smtp down");
    }

    @Test
    @DisplayName("SEND_EMAIL failure at attempts=3: cap already exceeded, row stays FAILED and attempts grows to 4 (no upper clamp)")
    void relayPendingEventsFailureBeyondCapStillIncrementsAttempts() {
        EventOutbox row = waitingRow(3, defaultPayload());
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendGreetingEmail(any(UserRegisteredEvent.class));

        outboxRelayService.relayPendingEvents();

        // Pin: attempts can exceed the cap of 3 — there is no upper clamp in the relay.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        EventOutbox saved = savedRowCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(saved.getAttempts()).isEqualTo(4);
        assertThat(saved.getLastError()).isEqualTo("RuntimeException: smtp down");
    }

    @Test
    @DisplayName("No WAITING rows: no email is sent and nothing is saved")
    void relayPendingEventsWithNoWaitingRowsDoesNothing() {
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of());

        outboxRelayService.relayPendingEvents();

        verifyNoInteractions(emailService);
        verify(eventOutboxRepository, never()).save(any(EventOutbox.class));
    }

    @Test
    @DisplayName("Error formatting unwraps the root cause: outer RuntimeException is dropped in favor of IllegalStateException")
    void relayPendingEventsRecordsRootCauseInsteadOfWrapperException() {
        EventOutbox row = waitingRow(0, defaultPayload());
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();
        doThrow(new RuntimeException("outer", new IllegalStateException("root cause msg")))
                .when(emailService).sendGreetingEmail(any(UserRegisteredEvent.class));

        outboxRelayService.relayPendingEvents();

        // formatError walks down getCause() to the root: "SimpleName: message" of the ROOT cause.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        assertThat(savedRowCaptor.getValue().getLastError())
                .isEqualTo("IllegalStateException: root cause msg");
    }

    @Test
    @DisplayName("Error formatting truncates messages longer than 500 characters to 500 chars plus '...'")
    void relayPendingEventsTruncatesLongErrorMessages() {
        String longMessage = "x".repeat(600);
        EventOutbox row = waitingRow(0, defaultPayload());
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();
        doThrow(new RuntimeException(longMessage))
                .when(emailService).sendGreetingEmail(any(UserRegisteredEvent.class));

        outboxRelayService.relayPendingEvents();

        // Exact equality: type prefix + exactly the first 500 characters + "...".
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        assertThat(savedRowCaptor.getValue().getLastError())
                .isEqualTo("RuntimeException: " + longMessage.substring(0, 500) + "...");
    }

    @Test
    @DisplayName("Payload without greetingText: relay still sends the event with greetingText=null (the fallback lives in EmailService, not the relay)")
    void relayPendingEventsSendsEventWithNullGreetingTextWhenPayloadKeyIsAbsent() {
        EventOutbox row = waitingRow(0, Map.of(
                "login", "alice",
                "email", "a@x.com",
                "eventId", EVENT_ID));
        when(eventOutboxRepository.findByStatus(OutboxStatus.WAITING)).thenReturn(List.of(row));
        stubSaveToReturnItsArgument();

        outboxRelayService.relayPendingEvents();

        // The relay forwards the record as-is; a missing payload key becomes a null field.
        verify(emailService).sendGreetingEmail(sentEventCaptor.capture());
        UserRegisteredEvent sent = sentEventCaptor.getValue();
        assertThat(sent.greetingText()).isNull();
        assertThat(sent.login()).isEqualTo("alice");
        assertThat(sent.email()).isEqualTo("a@x.com");
        assertThat(sent.eventId()).isEqualTo(EVENT_ID);

        // The send itself is (mocked as) successful, so the row is still marked SENT.
        verify(eventOutboxRepository).save(savedRowCaptor.capture());
        assertThat(savedRowCaptor.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Builds a fresh WAITING row of the given type/attempts with the given payload. */
    private EventOutbox waitingRow(int attempts, Map<String, Object> payload) {
        EventOutbox row = new EventOutbox();
        row.setId(UUID.randomUUID());
        row.setType(OutboxEventType.SEND_EMAIL);
        row.setStatus(OutboxStatus.WAITING);
        row.setAttempts(attempts);
        row.setPayload(payload);
        return row;
    }

    /** The standard full payload used by the failure-path tests. */
    private Map<String, Object> defaultPayload() {
        return Map.of(
                "login", "alice",
                "email", "a@x.com",
                "greetingText", "Hello alice !",
                "eventId", EVENT_ID);
    }

    /** Makes {@code save(...)} echo its argument so callers can assert on the persisted state. */
    private void stubSaveToReturnItsArgument() {
        when(eventOutboxRepository.save(any(EventOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
