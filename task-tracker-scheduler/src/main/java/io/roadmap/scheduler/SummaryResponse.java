package io.roadmap.scheduler;

import java.util.List;
import java.util.UUID;

public class SummaryResponse {
    public UUID id;
    public String login;
    public List<String> tasks;

    public SummaryResponse(UUID id, String login, List<String> summary) {
        this.id = id;
        this.login = login;
        this.tasks = summary;
    }
}