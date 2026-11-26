package io.roadmap.tasktracker.emailsender.services;

import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.repositories.EventInboxRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure mock-based unit tests for {@link EmailService}.
 * No Spring context, no real SMTP: the {@link JavaMailSender} is a Mockito mock and the
 * {@code @Value} field {@code fromAddress} is injected via {@link ReflectionTestUtils}.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM_ADDRESS = "noreply@test.local";

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private EventInboxRepository eventInboxRepository; // declared by the constructor, unused by the tested methods

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(emailSender, eventInboxRepository);
        ReflectionTestUtils.setField(service, "fromAddress", FROM_ADDRESS);
    }

    @Test
    @DisplayName("sendSimpleEmail: mockDelivery=true -> System.out path, mail sender never touched")
    void sendSimpleEmailWithMockDeliverySkipsMailSender() {
        service.sendSimpleEmail("bob@test.local", "Subject", "Body", true);

        // Pinned contract: mock delivery must not create or send anything.
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("sendSimpleEmail: mockDelivery=false -> exactly one SimpleMailMessage with from/to/subject/text")
    void sendSimpleEmailSendsExactSimpleMailMessage() {
        service.sendSimpleEmail("bob@test.local", "Subject", "Body", false);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emailSender).send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();
        assertThat(sent.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(sent.getTo()).containsExactly("bob@test.local");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getText()).isEqualTo("Body");
    }

    @Test
    @DisplayName("sendGreetingEmail: null greetingText -> fallback 'Hello alice !' built from login, real (non-mock) delivery")
    void sendGreetingEmailWithNullGreetingTextBuildsFallbackText() {
        UserRegisteredEvent event = new UserRegisteredEvent("alice", "alice@test.local", null, "11111111-2222-3333-4444-555555555555");

        service.sendGreetingEmail(event);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emailSender).send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();
        // Pinned contract: unlike sendSimpleEmail's report path, greeting delivery is always real (mockDelivery=false).
        assertThat(sent.getTo()).containsExactly("alice@test.local");
        assertThat(sent.getSubject()).isEqualTo("Successfully registration");
        assertThat(sent.getText()).isEqualTo("Hello alice !");
    }

    @Test
    @DisplayName("sendGreetingEmail: present greetingText -> used verbatim, no fallback construction")
    void sendGreetingEmailWithPresentGreetingTextUsesItVerbatim() {
        UserRegisteredEvent event = new UserRegisteredEvent("alice", "alice@test.local", "Welcome aboard, alice!", "11111111-2222-3333-4444-555555555555");

        service.sendGreetingEmail(event);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText()).isEqualTo("Welcome aboard, alice!");
    }

    @Test
    @DisplayName("sendGreetingEmail: mail send failure -> the very same MailSendException propagates out (logged then rethrown)")
    void sendGreetingEmailRethrowsSameSendExceptionInstance() {
        UserRegisteredEvent event = new UserRegisteredEvent("alice", "alice@test.local", "Hi!", "11111111-2222-3333-4444-555555555555");
        MailSendException failure = new MailSendException("smtp down");
        doThrow(failure).when(emailSender).send(any(SimpleMailMessage.class));

        // Pinned contract: catch-log-rethrow must preserve the original exception instance.
        assertThatThrownBy(() -> service.sendGreetingEmail(event))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("sendEmailWithAttachment: missing classpath attachment -> FileNotFoundException from ResourceUtils before addAttachment")
    void sendEmailWithAttachmentWithMissingResourceThrowsFileNotFoundException() {
        when(emailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        // ResourceUtils.getFile resolves the classpath resource eagerly; the missing file fails
        // before MimeMessageHelper.addAttachment is ever reached.
        assertThatThrownBy(() ->
                service.sendEmailWithAttachment("bob@test.local", "Subject", "Body", "classpath:does-not-exist.pdf"))
                .isInstanceOf(FileNotFoundException.class);

        verify(emailSender).createMimeMessage();
        verify(emailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
    }
}
