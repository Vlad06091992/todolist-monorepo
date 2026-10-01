package io.roadmap.todolistmonorepo.mappers;

import io.roadmap.todolistmonorepo.dto.TaskCreateRequest;
import io.roadmap.todolistmonorepo.dto.TaskResponse;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.entities.User;
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

    @Mapping(target = "isFinished", constant = "false")
    @Mapping(target = "user", expression = "java(new TaskResponse.UserData(userId, userLogin))")
    public abstract TaskResponse toResponse(Task task, String userId, String userLogin);

}