package io.roadmap.todolistmonorepo.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskCreateRequest(
        @NotBlank(message = "Description is required")
        String description
) {
}
