package io.roadmap.todolistmonorepo.controller;


import io.roadmap.todolistmonorepo.dto.AuthRequest;
import io.roadmap.todolistmonorepo.dto.AuthResponse;
import io.roadmap.todolistmonorepo.dto.UserCreateRequest;
import io.roadmap.todolistmonorepo.services.AuthService;
import io.roadmap.todolistmonorepo.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks")
public class TasksController { }