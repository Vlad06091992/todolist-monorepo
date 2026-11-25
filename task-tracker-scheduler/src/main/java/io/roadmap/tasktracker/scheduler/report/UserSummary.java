package io.roadmap.tasktracker.scheduler.report;

import java.util.List;
import java.util.UUID;

public class UserSummary {
    public UUID id;
    public String login;
    public String email;
    public List<String> tasks;

    public UserSummary(UUID id, String login, String email, List<String> tasks) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.tasks = tasks;
    }
}