package io.roadmap.tasktracker.backend.configuration;


import io.roadmap.tasktracker.backend.services.JwtAuthenticationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationManager authenticationManager;

    public SecurityConfig(JwtAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationManager.authenticationConverter());
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf((csrf) ->
                        csrf.disable()
                )
                .authenticationManager(authenticationManager)
                .addFilterAt(jwtFilter, org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange((exchanges) ->
                        exchanges
                                .pathMatchers("/auth/sign-in", "/auth/sign-up", "/tasks/reports/users-with-finished-tasks", "/tasks/reports/users-with-finished-tasks/count")
                                .permitAll()
                                .anyExchange()
                                .authenticated()
                )
                .build();
    }
}