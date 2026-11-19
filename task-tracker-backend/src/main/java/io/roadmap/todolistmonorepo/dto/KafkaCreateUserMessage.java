package io.roadmap.todolistmonorepo.dto;

public record KafkaCreateUserMessage(
        String login,
        String email,
        String message,
        String eventId
) {
    public KafkaCreateUserMessage(String login, String email, String eventId) {
        this(login, email, "Hello " + login + " !", eventId);
    }
}
