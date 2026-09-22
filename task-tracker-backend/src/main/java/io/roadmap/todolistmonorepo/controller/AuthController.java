package io.roadmap.todolistmonorepo.controller;


import io.roadmap.todolistmonorepo.configuration.JWTUtil;
import io.roadmap.todolistmonorepo.dto.AuthRequest;
import io.roadmap.todolistmonorepo.dto.AuthResponse;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final JWTUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest authRequest) {
        return userService.findByUsername(authRequest.username())
                .map(userDetails -> {
                    if (userDetails.getPassword().equals(authRequest.password())) {
                        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(authRequest.username())));
                    } else {
                        throw new BadCredentialsException("Invalid username or password");
                    }
                }).switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")));
    }
    @PostMapping("/signup")
    public Mono<ResponseEntity<String>> signup(@RequestBody User user) {
        user.setPassword(user.getPassword());
        return userService.save(user)
                .map(savedUser -> ResponseEntity.ok("User signed up successfully"));
    }

    @GetMapping("/protected")
    public Mono<ResponseEntity<String>> protectedEndpoint() {
        return Mono.just(ResponseEntity.ok("You have accessed a protected endpoint!"));
    }
}