package io.roadmap.tasktracker.emailsender.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {
    private final OutboxRelayService outboxRelayService;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class.getName());

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    @Scheduled(fixedRate = 5000)
    public void relayPendingEvents() {
        log.info("Outbox relay tick");
        outboxRelayService.relayPendingEvents();
    }
}