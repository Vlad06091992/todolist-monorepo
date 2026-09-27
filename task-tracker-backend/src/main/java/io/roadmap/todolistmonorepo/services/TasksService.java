package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.TaskCreateRequest;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.repositories.TasksRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TasksService {
    private final TasksRepository tasksRepository;
    private final Scheduler dbScheduler;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public Mono<Task> create(TaskCreateRequest taskDto, String userId){
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
            User user = entityManager.getReference(User.class, UUID.fromString(userId));
            Task task = new Task();
            task.setUser(user);
            task.setDescription(taskDto.description());
            task.setFinished(false);
            entityManager.persist(task);
            entityManager.flush();
            return task;
        }))
                .subscribeOn(dbScheduler);


    }

    public List<Task> getAll(){
        return tasksRepository.findAll();
    }

}
