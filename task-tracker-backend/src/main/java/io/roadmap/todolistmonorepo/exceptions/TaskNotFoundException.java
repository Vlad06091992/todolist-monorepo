package io.roadmap.todolistmonorepo.exceptions;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("task not found");
    }
}