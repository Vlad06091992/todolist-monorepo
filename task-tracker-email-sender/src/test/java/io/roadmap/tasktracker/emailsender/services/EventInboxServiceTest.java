package io.roadmap.tasktracker.emailsender.services;

import io.roadmap.tasktracker.emailsender.entities.EventInbox;
import io.roadmap.tasktracker.emailsender.entities.EventOutbox;
import io.roadmap.tasktracker.emailsender.entities.OutboxEventType;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.repositories.EventInboxRepository;
import io.roadmap.tasktracker.emailsender.repositories.EventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure mock-based unit tests for {@link EventInboxService}.
 * No Spring context, no database: repositories are Mockito mocks and the
 * {@code @Transactional} annotation is irrelevant at the unit level.
 */
@ExtendWith(MockitoExtension.class)
class EventInboxServiceTest {

    private static final String EVENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private EventInboxRepository eventInboxRepository;

    @InjectMocks
    private EventInboxService service;

    @Captor
    private ArgumentCaptor<EventInbox> inboxCaptor;

    @Captor
    private ArgumentCaptor<EventOutbox> outboxCaptor;

    private UserRegisteredEvent event(String eventId) {
        return new UserRegisteredEvent("alice", "alice@test.local", "Welcome!", eventId);
    }

    @Test
    @DisplayName("registerProcessedEvent: null eventId -> no repository interaction at all")
    void registerProcessedEventWithNullEventIdDoesNothing() {
        service.registerProcessedEvent(event(null));

        // Pinned contract: a malformed event must be silently dropped without touching persistence.
        verifyNoInteractions(eventInboxRepository);
        verifyNoInteractions(eventOutboxRepository);
    }

    @Test
    @DisplayName("registerProcessedEvent: duplicate eventId -> dedupe, no saves on either repository")
    void registerProcessedEventWithDuplicateEventIdDoesNotSave() {
        EventInbox existing = new EventInbox();
        existing.setEventId(EVENT_UUID);
        when(eventInboxRepository.findByEventId(EVENT_UUID)).thenReturn(Optional.of(existing));

        service.registerProcessedEvent(event(EVENT_ID));

        // Pinned contract: Kafka redelivery of an already-processed event must not write again.
        verify(eventInboxRepository).findByEventId(EVENT_UUID);
        verify(eventInboxRepository, org.mockito.Mockito.never()).save(any(EventInbox.class));
        verifyNoInteractions(eventOutboxRepository);
    }

    @Test
    @DisplayName("registerProcessedEvent: happy path -> inbox saved with eventId+processedAt, outbox links inbox and carries SEND_EMAIL payload")
    void registerProcessedEventHappyPathSavesInboxAndOutbox() {
        when(eventInboxRepository.save(any(EventInbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventOutboxRepository.save(any(EventOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerProcessedEvent(event(EVENT_ID));

        verify(eventInboxRepository).save(inboxCaptor.capture());
        EventInbox savedInbox = inboxCaptor.getValue();
        assertThat(savedInbox.getEventId()).isEqualTo(EVENT_UUID);
        assertThat(savedInbox.getProcessedAt()).isNotNull();

        verify(eventOutboxRepository).save(outboxCaptor.capture());
        EventOutbox savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getType()).isEqualTo(OutboxEventType.SEND_EMAIL);
        assertThat(savedOutbox.getEventInbox()).isSameAs(savedInbox);

        Map<String, Object> payload = savedOutbox.getPayload();
        assertThat(payload)
                .containsEntry("login", "alice")
                .containsEntry("email", "alice@test.local")
                .containsEntry("eventId", EVENT_ID);
    }

    @Test
    @DisplayName("registerProcessedEvent: malformed eventId -> IllegalArgumentException propagates, nothing saved (Kafka redelivery semantics)")
    void registerProcessedEventWithMalformedEventIdThrowsAndSavesNothing() {
        // "not-a-uuid" blows up inside UUID.fromString(...) while building the lookup argument,
        // before any repository call; the exception must propagate so the Kafka listener can
        // apply its redelivery/error handling.
        assertThatThrownBy(() -> service.registerProcessedEvent(event("not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(eventInboxRepository);
        verifyNoInteractions(eventOutboxRepository);
    }
}
