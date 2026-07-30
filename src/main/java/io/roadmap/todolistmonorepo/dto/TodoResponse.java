package io.roadmap.todolistmonorepo.dto;

import java.time.Instant;

public record TodoResponse(
        Long id,
        String title,
        boolean done,
        Instant createdAt) {
}
