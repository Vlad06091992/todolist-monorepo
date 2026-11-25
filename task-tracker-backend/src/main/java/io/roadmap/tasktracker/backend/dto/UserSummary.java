package io.roadmap.tasktracker.backend.dto;

import java.util.UUID;

public interface UserSummary {
    UUID getId();
    String getLogin();
    String getEmail();
    String getFinishedTaskDescriptions();
}