package io.roadmap.tasktrackeremailsender.kafka;

import io.roadmap.tasktrackeremailsender.controllers.EmailController;
import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = "EMAIL_SENDING_TASKS", groupId = "my-consumer-group")
public class KafkaMessageListener {
    @Autowired
    EmailService emailService;
    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageListener.class);


    @KafkaHandler
    public void onUserCreated(KafkaCreateUserMessage message) {

        try {
            emailService.sendSimpleEmail(message.email(), "Successfully registration", "Hello " + message.login() + " !");
            LOG.info("User created: {}", message);
        } catch (MailException mailException) {
            LOG.error("Error while sending out email", mailException);
        }
    }


    //TODO посмотреть как работает с неизвестным DTO
//    @KafkaHandler(isDefault = true)
//    public void onUnknown(Object message) {
//        System.out.println("Unknown message type: " + message);
//    }
}
