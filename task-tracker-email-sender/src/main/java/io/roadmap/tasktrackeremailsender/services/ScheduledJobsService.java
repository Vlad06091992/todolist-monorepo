package io.roadmap.tasktrackeremailsender.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.entities.EventOutbox;
import io.roadmap.tasktrackeremailsender.entities.OutboxStatus;
import io.roadmap.tasktrackeremailsender.repositories.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduledJobsService {
    EventOutboxRepository eventOutboxRepository;
    EmailService emailService;
    ObjectMapper objectMapper = new ObjectMapper();
    private final Logger log = LoggerFactory.getLogger(ScheduledJobsService.class.getName());

    public ScheduledJobsService(EventOutboxRepository eventOutboxRepository, EmailService emailService) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.emailService = emailService;
    }

    protected void sendEmails() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findByStatus(OutboxStatus.WAITING);

        eventOutboxes.forEach((eventOutbox -> {
            switch (eventOutbox.getType()) {
                case SEND_EMAIL -> {

                    try {
                        KafkaCreateUserMessage message = objectMapper.convertValue(eventOutbox.getPayload(), KafkaCreateUserMessage.class);
                        emailService.sendGreetingMessage(message);
                        eventOutbox.setStatus(OutboxStatus.SENT);
                        int newAttempts = eventOutbox.getAttempts() + 1;
                        eventOutbox.setAttempts(newAttempts);
                        eventOutboxRepository.save(eventOutbox);
                        log.info("email successfully sent email: {}, message: {}", message.email(), message.message());
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
