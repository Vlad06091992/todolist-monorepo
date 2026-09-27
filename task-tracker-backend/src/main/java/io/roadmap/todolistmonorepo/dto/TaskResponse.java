package io.roadmap.todolistmonorepo.dto;

//JsonN

//public class TaskResponse {
//    private class User{
//        private String id;
//        private String login;
//    }
//
//    private String id;
//    private User user;
//    private String description;
//    private Boolean isFinished;
//    private String createdAt;
//}

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