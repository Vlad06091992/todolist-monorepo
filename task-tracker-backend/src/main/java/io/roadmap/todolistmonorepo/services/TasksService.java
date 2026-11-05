package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.TaskCreateRequest;
import io.roadmap.todolistmonorepo.dto.TaskResponse;
import io.roadmap.todolistmonorepo.dto.UserSummary;
import io.roadmap.todolistmonorepo.dto.UserWithTasksResponse;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.exceptions.InternalServerException;
import io.roadmap.todolistmonorepo.exceptions.TaskNotFoundException;
import io.roadmap.todolistmonorepo.mappers.TaskMapper;
import io.roadmap.todolistmonorepo.mappers.UsersForReportMapper;
import io.roadmap.todolistmonorepo.repositories.TasksRepository;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
import liquibase.exception.DatabaseException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TasksService {
    private final TasksRepository tasksRepository;
    private final UsersRepository usersRepository;
    private final Scheduler dbScheduler;
    private final TransactionTemplate transactionTemplate;
    private final TaskMapper taskMapper;
    private final UsersForReportMapper usersForReportMapper;
    private static final Logger log = LoggerFactory.getLogger(TasksService.class.getName());


//    @PersistenceContext
//    private EntityManager entityManager;
    // TODO ознакомииться с функционалом EntityManager
//    public Mono<TaskResponse> create(TaskCreateRequest taskDto, String userId, String userLogin) {
//        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
//                    Task task = taskMapper.toEntity(taskDto, userId);
//                    entityManager.persist(task);
//                    entityManager.flush();
//                    return taskMapper.toResponse(task, userId, userLogin);
//                }))
//                .subscribeOn(dbScheduler);
//    }

    public Mono<TaskResponse> create(TaskCreateRequest taskDto, String userId, String userLogin) {
        return Mono.fromCallable(() -> {
                    Task task = new Task();
                    User user = usersRepository.getReferenceById(UUID.fromString(userId));
                    task.setUser(user);
                    task.setDescription(taskDto.description());
                    task.setFinished(false);
                    return tasksRepository.save(task);
                }).map(taskResponse -> taskMapper.toResponse(taskResponse, userId, userLogin, false))
                .onErrorResume(e -> switch (e) {
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }


    public Mono<Void> delete(String taskId, String userId) {
        return Mono.<Void>fromRunnable(() -> {
                    Integer count = tasksRepository.deleteTaskByIdAndUser(UUID.fromString(taskId), UUID.fromString(userId));
                    if (count == 0) {
                        throw new TaskNotFoundException();
                    }
                })
                .onErrorResume(e -> switch (e) {
                    case TaskNotFoundException ex -> Mono.error(new TaskNotFoundException());
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }


    public Mono<Void> update(String taskId, String userId, boolean finished) {
        return Mono.<Void>fromRunnable(() -> {
                    Integer count = tasksRepository.updateTaskByIdAndUser(UUID.fromString(taskId), UUID.fromString(userId), finished);
                    if (count == 0) {
                        throw new TaskNotFoundException();
                    }
                })
                .onErrorResume(e -> switch (e) {
                    case TaskNotFoundException ex -> Mono.error(new TaskNotFoundException());
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }

    public Flux<TaskResponse> getAllByUserId(UUID userId) {
        Flux<TaskResponse> res = Mono
                .fromCallable(() -> tasksRepository.findAllByUser_Id(userId))
                .flatMapMany(Flux::fromIterable)
                .map(t -> taskMapper.toResponse(t, t.getUser().getId().toString(), t.getUser().getLogin(), t.isFinished()))
                .onErrorResume(e -> switch (e) {
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
        return res;
    }

    public Mono<TaskResponse> getTaskById(String taskId) {
        return Mono
                .fromCallable(() -> tasksRepository.getTaskById(UUID.fromString(taskId)))
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new TaskNotFoundException()))
                .map(task -> {
                    User user = task.getUser();
                    return taskMapper.toResponse(task, user.getId().toString(), user.getLogin(), task.isFinished());
                })
                .onErrorResume(e -> switch (e) {
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }

    public Flux<TaskResponse>  getFinishedTasksAfterDate(UUID userId, LocalDateTime dateTime) {

        log.info("userId: {}",userId);
        log.info("dateTime: {}",dateTime);

        return Mono
                .fromCallable(() -> tasksRepository.getFinishedTasksAfterDate(userId, dateTime))
                .flatMapMany(Flux::fromIterable)
                .map(t -> taskMapper.toResponse(t, t.getUser().getId().toString(), t.getUser().getLogin(), t.isFinished()))
                .onErrorResume(e -> switch (e) {
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }

    public Flux<UserWithTasksResponse>  getUsersWithTasksForReport(LocalDateTime fromDate) {
//        log.info("dateTime: {}",dateTime);

        return Mono
                .fromCallable(() -> usersRepository.findAllSummaries(fromDate))
                .flatMapMany(Flux::fromIterable)
                .map(t -> usersForReportMapper.usersToReport(t))
                .onErrorResume(e -> switch (e) {
                    case DataAccessException ex -> Mono.error(new DatabaseException(ex));
                    default -> Mono.error(new InternalServerException(e.getMessage()));
                })
                .subscribeOn(dbScheduler);
    }
}
