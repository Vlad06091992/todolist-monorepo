package io.roadmap.scheduler;

import java.util.List;
import java.util.UUID;

public class SummaryResponse {
    public UUID id;
    public String login;
    public String email;
    public List<String> tasks;

    public SummaryResponse(UUID id, String login, String email, List<String> summary) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.tasks = summary;
    }
}