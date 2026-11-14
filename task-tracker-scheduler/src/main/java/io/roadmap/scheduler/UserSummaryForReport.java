package io.roadmap.scheduler;

import java.util.List;
import java.util.UUID;

public class UserSummaryForReport {
    public UUID id;
    public String login;
    public String email;
    public List<String> tasks;

    public UserSummaryForReport(UUID id, String login, String email, List<String> summary) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.tasks = summary;
    }
}