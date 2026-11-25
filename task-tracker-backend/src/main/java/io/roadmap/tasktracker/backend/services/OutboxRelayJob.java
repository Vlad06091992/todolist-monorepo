package io.roadmap.tasktracker.backend.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxRelayService outboxRelayService;
    private final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class.getName());

    @Scheduled(fixedRate = 5000)
    public void pollOutbox() {
        outboxRelayService.dispatchPendingOutboxEvents();
    }

}