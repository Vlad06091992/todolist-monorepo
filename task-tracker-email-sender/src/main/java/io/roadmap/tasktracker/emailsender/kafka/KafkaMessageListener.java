package io.roadmap.tasktracker.emailsender.kafka;

import io.roadmap.tasktracker.emailsender.event.EmailReportRequestedEvent;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.services.EmailService;
import io.roadmap.tasktracker.emailsender.services.EventInboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(
        topics = "${app.kafka.topic.email-requested}",
        groupId = "${spring.kafka.consumer.group-id}"
)
public class KafkaMessageListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageListener.class);

    private final EmailService emailService;
    private final EventInboxService eventInboxService;

    public KafkaMessageListener(EmailService emailService, EventInboxService eventInboxService) {
        this.emailService = emailService;
        this.eventInboxService = eventInboxService;
    }

    @KafkaHandler
    public void onUserCreated(UserRegisteredEvent event) {
        eventInboxService.registerProcessedEvent(event);
    }

    @KafkaHandler
    public void onEmailReportRequested(EmailReportRequestedEvent report) {
        try {
            emailService.sendSimpleEmail(report.email(), "Tasks report", "Hello " + report.message() + " !", true);
            log.info("Send report: {}", report);
        } catch (MailException mailException) {
            log.error("Error while sending out email", mailException);
        }
    }
}
