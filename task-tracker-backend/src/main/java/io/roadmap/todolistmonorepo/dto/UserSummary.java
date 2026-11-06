package io.roadmap.todolistmonorepo.dto;

import java.util.UUID;

public interface UserSummary {
    UUID getId();
    String getLogin();
    String getEmail();
    String getSummary();
}