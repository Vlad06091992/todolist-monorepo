package io.roadmap.tasktracker.backend.dto;

public record UserRegisteredEvent(
        String login,
        String email,
        String greetingText,
        String eventId
) {
    public UserRegisteredEvent(String login, String email, String eventId) {
        this(login, email, "Hello " + login + " !", eventId);
    }
}
