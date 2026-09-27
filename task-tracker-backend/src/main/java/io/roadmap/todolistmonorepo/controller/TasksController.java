package io.roadmap.todolistmonorepo.controller;


import io.roadmap.todolistmonorepo.dto.*;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.services.AuthService;
import io.roadmap.todolistmonorepo.services.JWTAuthenticationManager;
import io.roadmap.todolistmonorepo.services.TasksService;
import io.roadmap.todolistmonorepo.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks")
public class TasksController {

    private final TasksService tasksService;

    @PostMapping
    public Mono<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest taskDTO, Authentication authentication){

        JWTAuthenticationManager.Credentials credentials = (JWTAuthenticationManager.Credentials) authentication.getCredentials();
        Map<String, String> userData = Map.of("login", authentication.getPrincipal().toString(), "id", credentials.getId().toString());

//       Mono<Task> newTask = tasksService.create(taskDTO,credentials.getId().toString());

//        TaskResponse taskResponse = new TaskResponse(
//                newTask.getId().toString(),
//                credentials.getId().toString(),
//                authentication.getPrincipal().toString(),
//                newTask.getDescription(),
//                false,
//                newTask.getCreatedAt().toString()
//
//        );

        return tasksService.create(taskDTO,credentials.getId().toString()).map((newTask)-> new TaskResponse(
                 newTask.getId().toString(),
                 credentials.getId().toString(),
                 authentication.getPrincipal().toString(),
                 newTask.getDescription(),
                 false,
                 newTask.getCreatedAt().toString()));

    }

}