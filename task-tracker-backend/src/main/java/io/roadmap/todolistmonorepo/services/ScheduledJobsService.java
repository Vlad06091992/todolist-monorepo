package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.KafkaCreateUserMessage;
import io.roadmap.todolistmonorepo.entities.outbox.EventOutbox;
import io.roadmap.todolistmonorepo.entities.outbox.OutboxStatus;
import io.roadmap.todolistmonorepo.kafka.KafkaService;
import io.roadmap.todolistmonorepo.repositories.EventOutboxRepository;
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
public class ScheduledJobsService {

    private final Logger log = LoggerFactory.getLogger(ScheduledJobsService.class.getName());
    private final EventOutboxRepository eventOutboxRepository;
    private final KafkaService kafkaService;
    @Value("${app.kafka.topic.notifications-email-requested}")
    private String notificationsEmailRequested;
    private final Scheduler dbScheduler;
    private final ObjectMapper om;

    protected void SendEmailMessageToKafka() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findByStatus(OutboxStatus.WAITING);
        eventOutboxes.forEach((eventOutbox -> {
            switch (eventOutbox.getType()) {
                case USER_REGISTERED -> {
                    KafkaCreateUserMessage message = om.convertValue(eventOutbox.getPayload(), KafkaCreateUserMessage.class);
                    kafkaService
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


        log.info("preparatoryJob");
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
