package io.roadmap.tasktracker.backend.controller;


import io.roadmap.tasktracker.backend.dto.TaskCreateRequest;
import io.roadmap.tasktracker.backend.dto.TaskResponse;
import io.roadmap.tasktracker.backend.dto.TaskUpdateRequest;
import io.roadmap.tasktracker.backend.dto.UserWithTasksResponse;
import io.roadmap.tasktracker.backend.services.JwtAuthenticationManager;
import io.roadmap.tasktracker.backend.services.TasksService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks")
public class TasksController {

    private final TasksService tasksService;

    @PostMapping
    public Mono<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request, Authentication authentication) {
        JwtAuthenticationManager.AuthenticatedUserId credentials = (JwtAuthenticationManager.AuthenticatedUserId) authentication.getCredentials();
        return tasksService.create(request, credentials.getId().toString(), authentication.getPrincipal().toString());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteTask(@Valid @PathVariable String id, Authentication authentication) {
        JwtAuthenticationManager.AuthenticatedUserId credentials = (JwtAuthenticationManager.AuthenticatedUserId) authentication.getCredentials();
        return tasksService.delete(id, credentials.getId().toString()).thenReturn(ResponseEntity.noContent().build());

    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Void>> updateTask(@Valid @PathVariable String id, @RequestBody TaskUpdateRequest request, Authentication authentication) {
        JwtAuthenticationManager.AuthenticatedUserId credentials = (JwtAuthenticationManager.AuthenticatedUserId) authentication.getCredentials();
        return tasksService.update(id, credentials.getId().toString(), request.finished()).thenReturn(ResponseEntity.noContent().build());

    }

    @GetMapping("/{id}")
    public Mono<TaskResponse> getTask(@Valid @PathVariable String id, Authentication authentication) {
        return tasksService.findById(id);

    }


    @GetMapping
    public Flux<TaskResponse> getTasks(Authentication authentication) {
        return tasksService.findAllByUserId(((JwtAuthenticationManager.AuthenticatedUserId) authentication.getCredentials()).getId());
    }

    @GetMapping("/reports/users-with-finished-tasks")
    public Flux<UserWithTasksResponse> getUserReports(@RequestParam String from, @RequestParam @Min(value = 1, message = "pageSize должен быть >= 1") int pageSize, @RequestParam @Min(value = 1, message = "page должен быть >= 1") int page) {
        return tasksService.findUserReports(LocalDateTime.parse(from), pageSize, page);
    }


    @GetMapping("/reports/users-with-finished-tasks/count")
    public Mono<ResponseEntity<Long>> getReportCount(@RequestParam String from) {
        return tasksService.countUsersWithFinishedTasks(LocalDateTime.parse(from))
                .map(ResponseEntity::ok);
    }

}