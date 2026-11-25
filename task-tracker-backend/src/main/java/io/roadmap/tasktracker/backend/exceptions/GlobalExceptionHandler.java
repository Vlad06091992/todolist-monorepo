package io.roadmap.tasktracker.backend.exceptions;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.MissingRequestValueException;


import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MESSAGE_FIELD = "message";

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingRequestValueException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(MESSAGE_FIELD,ex.getBody().getDetail());
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new HashMap<>();

        String errorMessage = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("Ошибка валидации параметров запроса");

        errors.put(MESSAGE_FIELD,errorMessage);
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleWebExchangeBindException(WebExchangeBindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errors);
    }


    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Object> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        Map<String, String> response = Map.of("message",ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<Object> handleInternalServerErrorException(InternalServerErrorException ex) {
        Map<String, String> response = Map.of("message",ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Object> handleTaskNotFoundException(TaskNotFoundException ex) {
        Map<String, String> response = Map.of("message",ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
}