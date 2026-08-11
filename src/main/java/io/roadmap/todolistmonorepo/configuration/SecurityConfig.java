package io.roadmap.todolistmonorepo.configuration;

import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableWebFluxSecurity
public class SecurityConfig {

//    private final JWTAuthenticationManager authenticationManager;
//
//    public SecurityConfig(JWTAuthenticationManager authenticationManager) {
//        this.authenticationManager = authenticationManager;
//    }
//
//    @Bean
//    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//        return http
//                .csrf().disable()
//                .authorizeExchange()
//                .pathMatchers("/login","/signup").permitAll()
//                .anyExchange().authenticated()
//                .and()
//                .authenticationManager(authenticationManager)
//                .build();
//    }
}