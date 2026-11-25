package io.roadmap.tasktracker.backend.exceptions;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException() {
        super("User already exist");
    }
}