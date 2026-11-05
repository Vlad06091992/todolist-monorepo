package io.roadmap.todolistmonorepo.mappers;

import io.roadmap.todolistmonorepo.dto.UserSummary;
import io.roadmap.todolistmonorepo.dto.UserWithTasksResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class UsersForReportMapper {

    @Mapping(target = "tasks",
            expression = "java(entity.getSummary() == null ? null : UserWithTasksResponse.mapTasks(entity.getSummary()))")
    public abstract UserWithTasksResponse usersToReport(UserSummary entity);
}