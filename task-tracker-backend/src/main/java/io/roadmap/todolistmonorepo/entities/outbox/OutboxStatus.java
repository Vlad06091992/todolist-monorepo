package io.roadmap.todolistmonorepo.entities.outbox;

public enum OutboxStatus {
    WAITING,
    SENT,
    FAILED
}