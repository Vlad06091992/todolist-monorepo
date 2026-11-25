package io.roadmap.tasktracker.backend.exceptions;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("task not found");
    }
}