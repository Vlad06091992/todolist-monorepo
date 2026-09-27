package io.roadmap.todolistmonorepo.controller;


import io.roadmap.todolistmonorepo.dto.*;
import io.roadmap.todolistmonorepo.services.JWTAuthenticationManager;
import io.roadmap.todolistmonorepo.services.TasksService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks")
public class TasksController {

    private final TasksService tasksService;

    @PostMapping
    public Mono<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest taskDTO, Authentication authentication) {
        JWTAuthenticationManager.Credentials credentials = (JWTAuthenticationManager.Credentials) authentication.getCredentials();
        return tasksService.create(taskDTO, credentials.getId().toString(), authentication.getPrincipal().toString());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteTask(@Valid @PathVariable String id, Authentication authentication) {
        JWTAuthenticationManager.Credentials credentials = (JWTAuthenticationManager.Credentials) authentication.getCredentials();
        return tasksService.delete(id, credentials.getId().toString()).thenReturn(ResponseEntity.noContent().build());

    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Void>> updateTask(@Valid @PathVariable String id, @RequestBody TaskUpdateRequest taskDTO, Authentication authentication) {
        JWTAuthenticationManager.Credentials credentials = (JWTAuthenticationManager.Credentials) authentication.getCredentials();
        return tasksService.update(id, credentials.getId().toString(), taskDTO.finished()).thenReturn(ResponseEntity.noContent().build());

    }

    @GetMapping("/{id}")
    public Mono<TaskResponse> getTask(@Valid @PathVariable String id, Authentication authentication) {
        return tasksService.getTaskById(id);

    }


    @GetMapping
    public Flux<TaskResponse> getTasks(Authentication authentication) {
        return tasksService.getAllByUserId(((JWTAuthenticationManager.Credentials) authentication.getCredentials()).getId());
    }

}