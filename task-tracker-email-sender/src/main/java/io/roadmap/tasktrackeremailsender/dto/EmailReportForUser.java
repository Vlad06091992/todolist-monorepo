package io.roadmap.tasktrackeremailsender.dto;

public record EmailReportForUser (
        String login,
        String email,
        String message
) {
}
