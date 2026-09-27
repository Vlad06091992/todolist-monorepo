package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.TaskCreateRequest;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.repositories.TasksRepository;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TasksService {
    private final TasksRepository tasksRepository;
    private final UsersRepository usersRepository;
    private final Scheduler dbScheduler;
//    private final EntityManager entityManager;

    public Mono<Task> create(TaskCreateRequest taskDto, String userId){
        return Mono.fromCallable(()->{
            Task task = new Task();
            User user = usersRepository.getReferenceById(UUID.fromString(userId));
            task.setUser(user);
            task.setDescription(taskDto.description());
            task.setFinished(false);
            Task createdTask = tasksRepository.save(task);
            return createdTask;
        })
                .subscribeOn(dbScheduler);


    }

    public List<Task> getAll(){
        return tasksRepository.findAll();
    }

}
