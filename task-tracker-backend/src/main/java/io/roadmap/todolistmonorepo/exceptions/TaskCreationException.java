package io.roadmap.todolistmonorepo.exceptions;

public class TaskCreationException extends RuntimeException {
    public TaskCreationException(Throwable cause) {
        super("error during tasks create", cause);
    }
}