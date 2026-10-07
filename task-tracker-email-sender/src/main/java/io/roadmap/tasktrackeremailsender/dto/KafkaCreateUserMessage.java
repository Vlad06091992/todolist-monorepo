package io.roadmap.tasktrackeremailsender.dto;

public record KafkaCreateUserMessage(
        String login,
        String email
) { }
