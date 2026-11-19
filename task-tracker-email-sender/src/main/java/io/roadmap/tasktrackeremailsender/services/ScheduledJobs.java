package io.roadmap.tasktrackeremailsender.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobs {
    private final ScheduledJobsService scheduledJobsService;
    private final Logger log = LoggerFactory.getLogger(ScheduledJobs.class.getName());

    public ScheduledJobs(ScheduledJobsService scheduledJobsService) {
        this.scheduledJobsService = scheduledJobsService;
    }

    @Scheduled(fixedRate = 5000)
    public void sendEmails() {
        log.info("sendEmails job");
        scheduledJobsService.sendEmails();
    }
}