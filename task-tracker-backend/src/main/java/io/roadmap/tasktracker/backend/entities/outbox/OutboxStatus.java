package io.roadmap.tasktracker.backend.entities.outbox;

public enum OutboxStatus {
    WAITING,
    SENT,
    FAILED
}