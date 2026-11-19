package io.roadmap.todolistmonorepo.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final ScheduledJobsService scheduledJobsService;
    private final Logger log = LoggerFactory.getLogger(ScheduledJobs.class.getName());

    @Scheduled(fixedRate = 5000)
    public void preparatoryJob() {
        scheduledJobsService.SendEmailMessageToKafka();
    }

}