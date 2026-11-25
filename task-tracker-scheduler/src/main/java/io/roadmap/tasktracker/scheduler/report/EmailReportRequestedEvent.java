package io.roadmap.tasktracker.scheduler.report;

public record EmailReportRequestedEvent(
        String login,
        String message,
        String email
) {

    public EmailReportRequestedEvent withEmail(String email) {
        return new EmailReportRequestedEvent(this.login, this.message, email);
    }

}
