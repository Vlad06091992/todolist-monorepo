package io.roadmap.todolistmonorepo.services;


import io.roadmap.todolistmonorepo.configuration.JWTUtil;
import io.roadmap.todolistmonorepo.exceptions.InternalServerException;
import io.roadmap.todolistmonorepo.exceptions.JwtAuthenticationException;
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
public class JWTAuthenticationManager implements ReactiveAuthenticationManager {

    @Getter
    public class Credentials {

        private UUID id;

        public Credentials(UUID id) {
            this.id = id;
        }
    }

    private final JWTUtil jwtUtil;
    private final UserService userService;

    public JWTAuthenticationManager(JWTUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        return Mono.defer(() -> {
            String token = authentication.getCredentials().toString();
            String username = jwtUtil.extractUsername(token);
            return userService.findByLogin(username);
        }).<Authentication>map(userDetails -> {
            Credentials credentials = new Credentials(userDetails.getId());
            return new UsernamePasswordAuthenticationToken(
                    userDetails.getLogin(), credentials, Collections.emptyList());
        }).onErrorResume((e) -> {
            return Mono.error(new JwtAuthenticationException("Invalid JWT token: " + e.getMessage()));
        });
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