package io.roadmap.tasktrackeremailsender.kafka;

import io.roadmap.tasktrackeremailsender.dto.EmailReportForUser;
import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    EmailService emailService;
    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageListener.class);

    @KafkaHandler
    public void onUserCreated(KafkaCreateUserMessage message) {
        try {
            emailService.sendSimpleEmail(message.email(), "Successfully registration", "Hello " + message.login() + " !", true);
            LOG.info("User created: {}", message);
        } catch (MailException mailException) {
            LOG.error("Error while sending out email", mailException);
        }
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
