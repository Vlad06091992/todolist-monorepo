package io.roadmap.tasktracker.emailsender.event;

public record UserRegisteredEvent(
        String login,
        String email,
        String greetingText,
        String eventId
) { }
