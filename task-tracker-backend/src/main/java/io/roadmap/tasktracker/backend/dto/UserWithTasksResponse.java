package io.roadmap.tasktracker.backend.dto;

import java.util.Arrays;
import java.util.List;

public record UserWithTasksResponse (
        String login,
        String id,
        String email,
        List<String> tasks
){
    public static List<String> parseTaskSummary(String summary) {
        if (summary == null || summary.isBlank()) return List.of();
        return Arrays.stream(summary.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
