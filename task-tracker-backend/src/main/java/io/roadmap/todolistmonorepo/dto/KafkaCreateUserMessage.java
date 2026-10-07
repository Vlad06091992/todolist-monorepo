package io.roadmap.todolistmonorepo.dto;

public record KafkaCreateUserMessage(
        String login,
        String email
) { }
