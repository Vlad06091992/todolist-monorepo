package io.roadmap.todolistmonorepo.dto;
import io.roadmap.todolistmonorepo.entities.Task;

import java.util.Arrays;
import java.util.List;

public record UserWithTasksResponse (
        String login,
        String id,
        String email,
        List<String> tasks
){

    // Дополнительный конструктор — принимает плоские аргументы
    public static List<String> mapTasks(String summary) {
        if (summary == null || summary.isBlank()) return List.of();
        return Arrays.stream(summary.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

}
