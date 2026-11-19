package io.roadmap.tasktrackeremailsender.kafka;

import io.roadmap.tasktrackeremailsender.dto.EmailReportForUser;
import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.repositories.EventInboxRepository;
import io.roadmap.tasktrackeremailsender.services.EmailService;
import io.roadmap.tasktrackeremailsender.services.ProcessedEventsService;
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
    EmailService emailService;
    EventInboxRepository eventInboxRepository;
    ProcessedEventsService processedEventsService;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageListener.class);

    public KafkaMessageListener(EmailService emailService, EventInboxRepository eventInboxRepository, ProcessedEventsService processedEventsService) {
        this.emailService = emailService;
        this.eventInboxRepository = eventInboxRepository;
        this.processedEventsService = processedEventsService;

    }

    @KafkaHandler
    public void onUserCreated(KafkaCreateUserMessage message) {
      processedEventsService.registerEvent(message);
    }

    @KafkaHandler
    public void sendReport(EmailReportForUser message) {
        try {
            emailService.sendSimpleEmail(message.email(), "Tasks report", "Hello " + message.message() + " !", true);
            LOG.info("Send report: {}", message);
        } catch (MailException mailException) {
            LOG.error("Error while sending out email", mailException);
        }
    }
}
