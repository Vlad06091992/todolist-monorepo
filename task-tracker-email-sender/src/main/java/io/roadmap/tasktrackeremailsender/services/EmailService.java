package io.roadmap.tasktrackeremailsender.services;

import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.entities.EventInbox;
import io.roadmap.tasktrackeremailsender.repositories.EventInboxRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailService {

    private final JavaMailSender emailSender;
    private EventInboxRepository eventInboxRepository;

    @Value("${spring.mail.username}")
    private String from;

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);


    public EmailService(JavaMailSender emailSender, EventInboxRepository eventInboxRepository) {
        this.emailSender = emailSender;
        this.eventInboxRepository = eventInboxRepository;
    }


    public void sendSimpleEmail(String toAddress, String subject, String message, boolean isTest) {

        if(isTest){
            System.out.println("(MOCK) message: " + message + " was sent");
            return;
        }

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(from);
        simpleMailMessage.setTo(toAddress);
        simpleMailMessage.setSubject(subject);
        simpleMailMessage.setText(message);
        emailSender.send(simpleMailMessage);
    }

    public void sendEmailWithAttachment(String toAddress, String subject, String message, String attachment) throws MessagingException, FileNotFoundException, MessagingException {
        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);
        messageHelper.setTo(toAddress);
        messageHelper.setSubject(subject);
        messageHelper.setText(message);
        FileSystemResource file = new FileSystemResource(ResourceUtils.getFile(attachment));
        messageHelper.addAttachment("Purchase Order", file);
        emailSender.send(mimeMessage);
    }

    public void sendGreetingMessage(KafkaCreateUserMessage message){
        try {
            String msg = message.message() == null ? "Hello " + message.login() + " !" : message.message();
            sendSimpleEmail(message.email(), "Successfully registration", msg, false);
            LOG.info("User created: {}, eventId :{}", message, message.eventId());
        } catch (RuntimeException runtimeException) {
            LOG.error("Error while sending out email", runtimeException);
            throw runtimeException;
        }
    }
}