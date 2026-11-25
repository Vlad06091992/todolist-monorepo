package io.roadmap.tasktracker.backend.controller;

import io.roadmap.tasktracker.backend.services.JwtAuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UsersController {
//    private final UserService userService;

    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, String>>> me(Authentication authentication) {
        JwtAuthenticationManager.AuthenticatedUserId credentials = (JwtAuthenticationManager.AuthenticatedUserId) authentication.getCredentials();
        Map<String, String> userData = Map.of("login", authentication.getPrincipal().toString(), "id", credentials.getId().toString());
        return Mono.just(ResponseEntity.ok(userData));
    }
}