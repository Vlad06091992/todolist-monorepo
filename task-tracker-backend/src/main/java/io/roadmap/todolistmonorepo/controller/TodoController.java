package io.roadmap.todolistmonorepo.controller;

import io.roadmap.todolistmonorepo.dto.TodoRequest;
import io.roadmap.todolistmonorepo.dto.TodoResponse;
import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.services.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@RestController
@RequestMapping(value = "/api/todos", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final Scheduler dbScheduler;

//    @GetMapping
//    public Flux<TodoResponse> findAll() {
//        return Flux.just(
//                new TodoResponse(1L, "Написать контроллер", true, Instant.now()),
//                new TodoResponse(2L, "Подключить сервис", false, Instant.now()));
//    }

    @GetMapping
    public Flux<Task> findAll() {
        return Mono
                .fromCallable(todoService::getAll)
                .subscribeOn(dbScheduler)
                .flatMapMany(Flux::fromIterable);
    }

//    public Flux<Task> findAll() {
//        return Flux.fromIterable(todoService.getAll());
//        return Flux.fromCallable();
    }

//    @GetMapping("/{id}")
//    public Mono<TodoResponse> findById(@PathVariable Long id) {
//        return Mono.just(new TodoResponse(id, "Заглушка", false, Instant.now()));
//    }

//    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
//    @ResponseStatus(HttpStatus.CREATED)
//    public Mono<TodoResponse> create(@RequestBody TodoRequest request) {
//        return Mono.just(new TodoResponse(42L, request.title(), false, Instant.now()));
//    }
//}
