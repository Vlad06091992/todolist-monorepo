package io.roadmap.tasktracker.backend.services;


import io.roadmap.tasktracker.backend.security.JwtTokenProvider;
import io.roadmap.tasktracker.backend.exceptions.InternalServerErrorException;
import io.roadmap.tasktracker.backend.exceptions.JwtAuthenticationException;
import lombok.Getter;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    @Getter
    public class AuthenticatedUserId {

        private UUID id;

        public AuthenticatedUserId(UUID id) {
            this.id = id;
        }
    }

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public JwtAuthenticationManager(JwtTokenProvider jwtTokenProvider, UserService userService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        return Mono.defer(() -> {
            String token = authentication.getCredentials().toString();
            String login = jwtTokenProvider.extractLogin(token);
            return userService.findByLogin(login);
        }).<Authentication>map(userDetails -> {
            AuthenticatedUserId credentials = new AuthenticatedUserId(userDetails.getId());
            return new UsernamePasswordAuthenticationToken(
                    userDetails.getLogin(), credentials, Collections.emptyList());
        }).onErrorResume((e) -> Mono.error(new JwtAuthenticationException("Invalid JWT token: " + e.getMessage())));
    }

    public ServerAuthenticationConverter authenticationConverter() {
        return exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
            }
            return Mono.empty();
        };
    }
}