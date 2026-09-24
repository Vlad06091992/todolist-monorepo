package io.roadmap.todolistmonorepo.exceptions;

public class UserAlreadyExistException extends RuntimeException {
    public UserAlreadyExistException() {
        super("User already exist");
    }
}