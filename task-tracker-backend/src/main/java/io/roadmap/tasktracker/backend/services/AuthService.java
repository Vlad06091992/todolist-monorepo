package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.security.JwtTokenProvider;
import io.roadmap.tasktracker.backend.dto.AuthRequest;
import io.roadmap.tasktracker.backend.dto.UserCreateRequest;
import io.roadmap.tasktracker.backend.entities.User;
import io.roadmap.tasktracker.backend.kafka.KafkaEventPublisher;
import io.roadmap.tasktracker.backend.mappers.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final Scheduler dbScheduler;
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final TransactionTemplate transactionTemplate;


    public Mono<String> login(AuthRequest authRequest) {

        return userService.findByLogin(authRequest.login())
                .map(userDetails -> {
                    if (passwordEncoder.matches(authRequest.password(), userDetails.getPassword())) {
                        return jwtTokenProvider.generateToken(authRequest.login());
                    } else {
                        throw new BadCredentialsException("Invalid login or password");
                    }
                })
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid login or password")))
                .subscribeOn(dbScheduler);
    }

    public Mono<User> register(UserCreateRequest request) {
        User user = userMapper.toEntity(request);
        return userService.registerUser(user);
    }
}