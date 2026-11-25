package io.roadmap.tasktracker.emailsender.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.entities.EventOutbox;
import io.roadmap.tasktracker.emailsender.entities.EventInbox;
import io.roadmap.tasktracker.emailsender.entities.OutboxEventType;
import io.roadmap.tasktracker.emailsender.repositories.EventOutboxRepository;
import io.roadmap.tasktracker.emailsender.repositories.EventInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventInboxService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(EventInboxService.class.getName());

    private final EventOutboxRepository eventOutboxRepository;
    private final EventInboxRepository eventInboxRepository;

    public EventInboxService(EventOutboxRepository eventOutboxRepository, EventInboxRepository eventInboxRepository) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.eventInboxRepository = eventInboxRepository;
    }

    @Transactional
    public void registerProcessedEvent(UserRegisteredEvent event) {
        if (event.eventId() == null) {
            log.error("event id required");
            return;
        }

        Optional<EventInbox> existingInboxEntry = eventInboxRepository.findByEventId(UUID.fromString(event.eventId()));

        if (existingInboxEntry.isPresent()) {
            log.error("event already processed {}", event.eventId());
            return;
        }

        Map<String, Object> payload = OBJECT_MAPPER.convertValue(event, Map.class);

        EventInbox eventInbox = new EventInbox();
        eventInbox.setEventId(UUID.fromString(event.eventId()));
        eventInbox.setProcessedAt(LocalDateTime.now());
        EventInbox savedInbox = eventInboxRepository.save(eventInbox);

        EventOutbox eventOutbox = new EventOutbox();
        eventOutbox.setEventInbox(savedInbox);
        eventOutbox.setType(OutboxEventType.SEND_EMAIL);
        eventOutbox.setPayload(payload);
        eventOutboxRepository.save(eventOutbox);

        log.info("success");
    }
}
