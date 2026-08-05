package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.repositories.TasksRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {
    private final TasksRepository tasksRepository;

    void create(Task task){
        tasksRepository.save(task);
    }

    public List<Task> getAll(){
        return tasksRepository.findAll();
    }

}
