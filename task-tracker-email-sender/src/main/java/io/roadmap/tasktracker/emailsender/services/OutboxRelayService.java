package io.roadmap.tasktracker.emailsender.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.entities.EventOutbox;
import io.roadmap.tasktracker.emailsender.entities.OutboxStatus;
import io.roadmap.tasktracker.emailsender.repositories.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxRelayService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class.getName());

    private final EventOutboxRepository eventOutboxRepository;
    private final EmailService emailService;

    public OutboxRelayService(EventOutboxRepository eventOutboxRepository, EmailService emailService) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.emailService = emailService;
    }

    protected void relayPendingEvents() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findByStatus(OutboxStatus.WAITING);

        eventOutboxes.forEach((eventOutbox -> {
            switch (eventOutbox.getType()) {
                case SEND_EMAIL -> {

                    try {
                        UserRegisteredEvent event = OBJECT_MAPPER.convertValue(eventOutbox.getPayload(), UserRegisteredEvent.class);
                        emailService.sendGreetingEmail(event);
                        eventOutbox.setStatus(OutboxStatus.SENT);
                        int newAttempts = eventOutbox.getAttempts() + 1;
                        eventOutbox.setAttempts(newAttempts);
                        eventOutboxRepository.save(eventOutbox);
                        log.info("email successfully sent email: {}, message: {}", event.email(), event.greetingText());
                    } catch (Exception e) {
                        log.error("Failed to send mail event {}", eventOutbox.getId(), e);
                        int newAttempts = eventOutbox.getAttempts() + 1;
                        eventOutbox.setAttempts(newAttempts);
                        eventOutbox.setLastError(formatError(e));

                        if (newAttempts >= 3) {
                            eventOutbox.setStatus(OutboxStatus.FAILED);
                            log.error("Outbox event {} moved to FAILED after {} attempts", eventOutbox.getId(), newAttempts);
                        }
                        eventOutboxRepository.save(eventOutbox);
                    }
                }
                default -> {
                    log.error("Unknown outbox event type: {}. Event ID: {}, Payload: {}",
                            eventOutbox.getType(), eventOutbox.getId(), eventOutbox.getPayload());

                    eventOutbox.setStatus(OutboxStatus.FAILED);
                    eventOutboxRepository.save(eventOutbox);
                }
            }
        }));

    }

    private String formatError(Throwable e) {
        if (e == null) return "Unknown error (exception is null)";

        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String type = root.getClass().getSimpleName();
        String message = root.getMessage();

        if (message == null || message.isBlank()) {
            return type;
        }

        if (message.length() > 500) {
            message = message.substring(0, 500) + "...";
        }

        return type + ": " + message;
    }
}
