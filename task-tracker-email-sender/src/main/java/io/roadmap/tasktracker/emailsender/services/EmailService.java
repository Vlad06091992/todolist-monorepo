package io.roadmap.tasktracker.emailsender.services;

import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.repositories.EventInboxRepository;
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

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender emailSender;
    private final EventInboxRepository eventInboxRepository;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender emailSender, EventInboxRepository eventInboxRepository) {
        this.emailSender = emailSender;
        this.eventInboxRepository = eventInboxRepository;
    }

    public void sendSimpleEmail(String toAddress, String subject, String text, boolean mockDelivery) {
        if (mockDelivery) {
            System.out.println("(MOCK) message: " + text + " was sent");
            return;
        }

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(fromAddress);
        simpleMailMessage.setTo(toAddress);
        simpleMailMessage.setSubject(subject);
        simpleMailMessage.setText(text);
        emailSender.send(simpleMailMessage);
    }

    public void sendEmailWithAttachment(String toAddress, String subject, String text, String attachment) throws MessagingException, FileNotFoundException, MessagingException {
        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);
        messageHelper.setTo(toAddress);
        messageHelper.setSubject(subject);
        messageHelper.setText(text);
        FileSystemResource file = new FileSystemResource(ResourceUtils.getFile(attachment));
        messageHelper.addAttachment("Purchase Order", file);
        emailSender.send(mimeMessage);
    }

    public void sendGreetingEmail(UserRegisteredEvent event) {
        try {
            String greetingText = event.greetingText() == null ? "Hello " + event.login() + " !" : event.greetingText();
            sendSimpleEmail(event.email(), "Successfully registration", greetingText, false);
            log.info("User created: {}, eventId :{}", event, event.eventId());
        } catch (RuntimeException runtimeException) {
            log.error("Error while sending out email", runtimeException);
            throw runtimeException;
        }
    }
}
