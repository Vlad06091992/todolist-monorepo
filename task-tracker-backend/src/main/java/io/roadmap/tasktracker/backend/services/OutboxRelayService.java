package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.dto.UserRegisteredEvent;
import io.roadmap.tasktracker.backend.entities.outbox.EventOutbox;
import io.roadmap.tasktracker.backend.entities.outbox.OutboxStatus;
import io.roadmap.tasktracker.backend.kafka.KafkaEventPublisher;
import io.roadmap.tasktracker.backend.repositories.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final Logger log = LoggerFactory.getLogger(OutboxRelayService.class.getName());
    private final EventOutboxRepository eventOutboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    @Value("${app.kafka.topic.notifications-email-requested}")
    private String notificationsEmailRequested;
    private final Scheduler dbScheduler;
    private final ObjectMapper objectMapper;

    protected void dispatchPendingOutboxEvents() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findByStatus(OutboxStatus.WAITING);
        eventOutboxes.forEach((eventOutbox -> {
            switch (eventOutbox.getType()) {
                case USER_REGISTERED -> {
                    UserRegisteredEvent message = objectMapper.convertValue(eventOutbox.getPayload(), UserRegisteredEvent.class);
                    kafkaEventPublisher
                            .sendMessage(notificationsEmailRequested, message)
                            .then(Mono.fromRunnable(() -> {
                                eventOutbox.setStatus(OutboxStatus.SENT);
                                int newAttempts = eventOutbox.getAttempts() + 1;
                                eventOutbox.setAttempts(newAttempts);
                                eventOutboxRepository.save(eventOutbox);
                                log.info("Outbox event {} successfully sent", eventOutbox.getId());
                            }))
                            .onErrorResume(e -> {
                                log.error("Failed to send Outbox event {}", eventOutbox.getId(), e);
                                return Mono.fromRunnable(() -> {
                                    int newAttempts = eventOutbox.getAttempts() + 1;
                                    eventOutbox.setAttempts(newAttempts);

                                    eventOutbox.setLastError(formatError(e));

                                    if (newAttempts >= 3) {
                                        eventOutbox.setStatus(OutboxStatus.FAILED);
                                        log.error("Outbox event {} moved to FAILED after {} attempts", eventOutbox.getId(), newAttempts);
                                    }

                                    eventOutboxRepository.save(eventOutbox);
                                });
                            })
                            .subscribeOn(dbScheduler)
                            .subscribe(
                                    null,
                                    e -> log.error("Outbox processing failed", e)
                            );
                }
                default -> {
                    log.error("Unknown outbox event type: {}. Event ID: {}, Payload: {}",
                            eventOutbox.getType(), eventOutbox.getId(), eventOutbox.getPayload());

                    eventOutbox.setStatus(OutboxStatus.FAILED);
                    eventOutboxRepository.save(eventOutbox);
                }
            }
        }));


        log.info("Outbox dispatch cycle completed");
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
