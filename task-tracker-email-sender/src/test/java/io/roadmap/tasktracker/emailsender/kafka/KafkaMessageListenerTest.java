package io.roadmap.tasktracker.emailsender.kafka;

import io.roadmap.tasktracker.emailsender.event.EmailReportRequestedEvent;
import io.roadmap.tasktracker.emailsender.event.UserRegisteredEvent;
import io.roadmap.tasktracker.emailsender.services.EmailService;
import io.roadmap.tasktracker.emailsender.services.EventInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pure unit tests for {@link KafkaMessageListener}: mock-based, no Spring context, no embedded Kafka.
 *
 * <p>Both {@code @KafkaHandler} methods are synchronous {@code void} methods and are invoked
 * directly. The tests pin the deliberate asymmetry between the two handlers:
 * <ul>
 *   <li>{@code onUserCreated} has no try/catch — an {@link EventInboxService} failure
 *       propagates out of the handler (dedup failures must not be hidden);</li>
 *   <li>{@code onEmailReportRequested} catches {@code MailException} — SMTP problems are
 *       logged and swallowed, and report emails are always mock-delivered ({@code mockDelivery=true}).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EventInboxService eventInboxService;

    @InjectMocks
    private KafkaMessageListener listener;

    @Captor
    private ArgumentCaptor<UserRegisteredEvent> inboxEventCaptor;

    @Test
    @DisplayName("onUserCreated registers the SAME UserRegisteredEvent instance with the inbox service")
    void onUserCreatedForwardsSameEventInstanceToInboxService() {
        UserRegisteredEvent event =
                new UserRegisteredEvent("alice", "a@x.com", "Hello alice !", "1b0f2b5e-6c1d-4a4e-9a3f-0a6c5d7e8f90");

        listener.onUserCreated(event);

        // Pin: the handler must pass the very same record instance through, without copying or re-creating it.
        verify(eventInboxService).registerProcessedEvent(inboxEventCaptor.capture());
        assertThat(inboxEventCaptor.getValue()).isSameAs(event);
    }

    @Test
    @DisplayName("onUserCreated does NOT swallow failures: a RuntimeException from the inbox service propagates out of the handler")
    void onUserCreatedPropagatesInboxRegistrationFailure() {
        UserRegisteredEvent event =
                new UserRegisteredEvent("alice", "a@x.com", "Hello alice !", "1b0f2b5e-6c1d-4a4e-9a3f-0a6c5d7e8f90");
        doThrow(new RuntimeException("db down"))
                .when(eventInboxService).registerProcessedEvent(any(UserRegisteredEvent.class));

        // Pin the asymmetry: unlike onEmailReportRequested, this handler has no try/catch at all.
        assertThatThrownBy(() -> listener.onUserCreated(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(eventInboxService).registerProcessedEvent(event);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("onEmailReportRequested sends the report email with exact args (subject 'Tasks report', 'Hello <message> !') and mockDelivery=true")
    void onEmailReportRequestedSendsReportEmailAlwaysMockDelivered() {
        EmailReportRequestedEvent report = new EmailReportRequestedEvent("bob", "b@x.com", "weekly");

        listener.onEmailReportRequested(report);

        // Pin all four arguments exactly — report emails are ALWAYS mock-delivered.
        verify(emailService).sendSimpleEmail("b@x.com", "Tasks report", "Hello weekly !", true);
        // The report handler must not touch the inbox service.
        verifyNoInteractions(eventInboxService);
    }

    @Test
    @DisplayName("onEmailReportRequested swallows MailException: MailSendException does not escape the handler")
    void onEmailReportRequestedSwallowsMailSendException() {
        EmailReportRequestedEvent report = new EmailReportRequestedEvent("bob", "b@x.com", "weekly");
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendSimpleEmail(anyString(), anyString(), anyString(), anyBoolean());

        // No exception escapes: the handler catches MailException and only logs it.
        assertThatCode(() -> listener.onEmailReportRequested(report)).doesNotThrowAnyException();

        // Pin: the send was still attempted with the exact arguments.
        verify(emailService).sendSimpleEmail("b@x.com", "Tasks report", "Hello weekly !", true);
    }
}
