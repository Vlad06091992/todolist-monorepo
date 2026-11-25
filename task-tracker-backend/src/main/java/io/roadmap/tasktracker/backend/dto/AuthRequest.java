package io.roadmap.tasktracker.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "Login is required")
        String login,
        @NotBlank(message = "Password is required")
        String password) {
}
