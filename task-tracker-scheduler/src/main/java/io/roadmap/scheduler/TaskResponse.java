package io.roadmap.scheduler;

public record TaskResponse(
        String id,
        User user,
        String description,
        Boolean isFinished,
        String createdAt
) {
    public record User(String id, String login) {}

    // Дополнительный конструктор — принимает плоские аргументы
    public TaskResponse(String id, String userId, String userLogin,
                        String description, Boolean isFinished, String createdAt) {
        this(id, new User(userId, userLogin), description, isFinished, createdAt);
    }
}