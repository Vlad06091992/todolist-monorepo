package io.roadmap.tasktracker.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskUpdateRequest(
        @NotBlank(message = "finished is required")
        Boolean finished
) {
}
