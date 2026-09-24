package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.configuration.JWTUtil;
import io.roadmap.todolistmonorepo.dto.AuthRequest;
import io.roadmap.todolistmonorepo.dto.UserCreateRequest;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JWTUtil jwtUtil;
    private final Scheduler dbScheduler;
    private final UsersRepository usersRepository;
    private final UserService userService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public Mono<String> login(AuthRequest authRequest) {

        return userService.findByLogin(authRequest.login())
                .map(userDetails -> {
                    if (bCryptPasswordEncoder.matches(authRequest.password(),userDetails.getPassword())) {
                        return jwtUtil.generateToken(authRequest.login());
                    } else {
                        throw new BadCredentialsException("Invalid username or password");
                    }
                })
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")))
                .subscribeOn(dbScheduler);
    }

    public Mono<User> createNewUser(UserCreateRequest userDTO) {
        User user = new User();
        user.setPassword(bCryptPasswordEncoder.encode(userDTO.password()));
        user.setLogin(userDTO.login());
        user.setEmail(user.getEmail());

       return userService.save(user);
    }
}