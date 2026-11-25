package io.roadmap.tasktracker.backend.mappers;

import io.roadmap.tasktracker.backend.dto.TaskCreateRequest;
import io.roadmap.tasktracker.backend.dto.TaskResponse;
import io.roadmap.tasktracker.backend.entities.Task;
import io.roadmap.tasktracker.backend.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class TaskMapper {

    @PersistenceContext
    private EntityManager entityManager;

    @Named("toUserReference")
    protected User toUserReference(String userId) {
        return entityManager.getReference(User.class, UUID.fromString(userId));
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", source = "userId", qualifiedByName = "toUserReference")
    @Mapping(target = "finished", constant = "false")
    public abstract Task toEntity(TaskCreateRequest dto, String userId);

    @Mapping(target = "user", expression = "java(new TaskResponse.UserData(userId, userLogin))")
    public abstract TaskResponse toResponse(Task task, String userId, String userLogin, boolean isFinished);

}