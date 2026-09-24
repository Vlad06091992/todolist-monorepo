package io.roadmap.todolistmonorepo.controller;

import io.roadmap.todolistmonorepo.services.JWTAuthenticationManager;
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
@RequestMapping("/user")
public class UsersController {
//    private final UserService userService;

    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, String>>> me(Authentication authentication) {
        JWTAuthenticationManager.Credentials credentials = (JWTAuthenticationManager.Credentials) authentication.getCredentials();
        Map<String, String> userData = Map.of("login", authentication.getPrincipal().toString(), "id", credentials.getId().toString());
        return Mono.just(ResponseEntity.ok(userData));
    }
}