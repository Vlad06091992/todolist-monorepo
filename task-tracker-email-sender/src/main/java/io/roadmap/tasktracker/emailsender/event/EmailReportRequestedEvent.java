package io.roadmap.tasktracker.emailsender.event;

public record EmailReportRequestedEvent (
        String login,
        String email,
        String message
) {
}
