package io.roadmap.tasktracker.backend.mappers;

import io.roadmap.tasktracker.backend.dto.UserSummary;
import io.roadmap.tasktracker.backend.dto.UserWithTasksResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class UserReportMapper {

    @Mapping(target = "tasks",
            expression = "java(entity.getFinishedTaskDescriptions() == null ? null : UserWithTasksResponse.parseTaskSummary(entity.getFinishedTaskDescriptions()))")
    public abstract UserWithTasksResponse toReport(UserSummary entity);
}