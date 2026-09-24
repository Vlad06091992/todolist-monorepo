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
    public Mono<ResponseEntity<String>> signup(@Valid @RequestBody UserCreateRequest userDTO) {
        return authService.createNewUser(userDTO)
                .map(savedUser -> ResponseEntity.ok("User signed up successfully"));
    }

    @GetMapping("/protected")
    public Mono<ResponseEntity<String>> protectedEndpoint() {
        return Mono.just(ResponseEntity.ok("You have accessed a protected endpoint!"));
    }
}