package io.roadmap.todolistmonorepo.controller;


import io.roadmap.todolistmonorepo.dto.TaskCreateRequest;
import io.roadmap.todolistmonorepo.dto.TaskResponse;
import io.roadmap.todolistmonorepo.dto.TaskUpdateRequest;
import io.roadmap.todolistmonorepo.dto.UserWithTasksResponse;
import io.roadmap.todolistmonorepo.services.JWTAuthenticationManager;
import io.roadmap.todolistmonorepo.services.TasksService;
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

//    @GetMapping("/get-finished-tasks-after-date")
//    public Flux<TaskResponse> getFinishedTasksAfterDate(@Valid @RequestParam String userId, String date) {
//        return tasksService.getFinishedTasksAfterDate(UUID.fromString(userId), LocalDateTime.parse(date));
//    }

    @GetMapping("/get-finished-tasks-after-date")
//    public void getUsersForReport(@Valid @RequestParam String userId, String date) {
//    public Flux<UserWithTasksResponse> getUsersForReport(@Valid @RequestParam String userId, String date) {
    public Flux<UserWithTasksResponse> getUsersForReport(@Valid @RequestParam String date,@RequestParam @Min(value = 1, message = "pageSize должен быть >= 1")  int pageSize,@RequestParam @Min(value = 1, message = "page должен быть >= 1")  int page) {
//        return tasksService.getUsersWithTasksForReport(LocalDateTime.parse(date));
        return tasksService.getUsersWithTasksForReport(LocalDateTime.parse(date), pageSize, page);
    }


    @GetMapping("/count")
//    public void getUsersForReport(@Valid @RequestParam String userId, String date) {
//    public Flux<UserWithTasksResponse> getUsersForReport(@Valid @RequestParam String userId, String date) {
    public Mono<Long> getCount(@Valid @RequestParam String date) {
//        return tasksService.getUsersWithTasksForReport(LocalDateTime.parse(date));
        return tasksService.getCount(LocalDateTime.parse(date));
    }

}