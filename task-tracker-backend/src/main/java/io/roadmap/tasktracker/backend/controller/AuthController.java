package io.roadmap.tasktracker.backend.controller;


import io.roadmap.tasktracker.backend.dto.AuthRequest;
import io.roadmap.tasktracker.backend.dto.AuthResponse;
import io.roadmap.tasktracker.backend.dto.UserCreateRequest;
import io.roadmap.tasktracker.backend.services.AuthService;
import io.roadmap.tasktracker.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/sign-in")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest authRequest) {
        return authService.login(authRequest)
                .map(token -> ResponseEntity.ok(new AuthResponse(token)));
    }
    @PostMapping("/sign-up")
    public Mono<ResponseEntity<String>> signup(@Valid @RequestBody UserCreateRequest request) {
        return authService.register(request)
                .map(savedUser -> ResponseEntity.ok("User signed up successfully"));
    }
}