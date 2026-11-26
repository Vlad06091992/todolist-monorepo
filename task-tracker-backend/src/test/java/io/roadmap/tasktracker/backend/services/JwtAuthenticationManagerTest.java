package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.entities.User;
import io.roadmap.tasktracker.backend.exceptions.JwtAuthenticationException;
import io.roadmap.tasktracker.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Mock-based unit tests for {@link JwtAuthenticationManager}. No Spring context.
 * The test lives in the same package so the inner class
 * {@link JwtAuthenticationManager.AuthenticatedUserId} can be referenced directly.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationManagerTest {

    private static final String TOKEN = "tok123";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @InjectMocks
    private JwtAuthenticationManager authenticationManager;

    private static User user(UUID id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setPassword("$2a$10$hashedpasswordhashedpasswordhashedpasswor");
        user.setEmail(login + "@example.com");
        return user;
    }

    private static Authentication authenticationWithRawToken(String rawToken) {
        // The converter produces an Authentication whose credentials carry the raw
        // token; authenticate() reads credentials().toString() as the token.
        return new UsernamePasswordAuthenticationToken("principal", rawToken);
    }

    @Test
    @DisplayName("authenticate with valid token and existing user yields authenticated principal with user id credentials")
    void authenticateValidTokenExistingUserReturnsPrincipalWithUserIdCredentials() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.extractLogin(TOKEN)).thenReturn("alice");
        when(userService.findByLogin("alice")).thenReturn(Mono.just(user(userId, "alice")));

        StepVerifier.create(authenticationManager.authenticate(authenticationWithRawToken(TOKEN)))
                .assertNext(result -> {
                    assertThat(result.isAuthenticated()).isTrue();
                    assertThat(result.getPrincipal()).isEqualTo("alice");
                    assertThat(result.getAuthorities()).isEmpty();
                    assertThat(result.getCredentials())
                            .isInstanceOf(JwtAuthenticationManager.AuthenticatedUserId.class);
                    JwtAuthenticationManager.AuthenticatedUserId credentials =
                            (JwtAuthenticationManager.AuthenticatedUserId) result.getCredentials();
                    assertThat(credentials.getId()).isEqualTo(userId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate with valid token but unknown user completes empty (behavior pin: no error is raised)")
    void authenticateUnknownUserCompletesEmptyPinningCurrentBehavior() {
        // Behavior pin: map() over an empty Mono propagates emptiness and
        // onErrorResume never fires, so a missing user yields an EMPTY Mono -
        // NOT a JwtAuthenticationException. Handling of the missing authentication
        // is left to the downstream security machinery.
        when(jwtTokenProvider.extractLogin(TOKEN)).thenReturn("ghost");
        when(userService.findByLogin("ghost")).thenReturn(Mono.empty());

        StepVerifier.create(authenticationManager.authenticate(authenticationWithRawToken(TOKEN)))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate wraps repository failures into JwtAuthenticationException (infra-error masking)")
    void authenticateWrapsRepositoryFailureIntoJwtAuthenticationException() {
        when(jwtTokenProvider.extractLogin(TOKEN)).thenReturn("alice");
        when(userService.findByLogin("alice")).thenReturn(Mono.error(new RuntimeException("db down")));

        StepVerifier.create(authenticationManager.authenticate(authenticationWithRawToken(TOKEN)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(JwtAuthenticationException.class);
                    assertThat(e).hasMessage("Invalid JWT token: db down");
                })
                .verify();
    }

    @Test
    @DisplayName("authenticate re-wraps JwtAuthenticationException from token parsing (type propagates)")
    void authenticateRewrapsJwtParsingFailure() {
        // Behavior pin: the original JwtAuthenticationException from extractLogin is
        // itself caught by onErrorResume and re-wrapped, so the surfaced exception is
        // a NEW JwtAuthenticationException whose message is prefixed with
        // "Invalid JWT token: ".
        when(jwtTokenProvider.extractLogin(TOKEN))
                .thenThrow(new JwtAuthenticationException("bad token"));

        StepVerifier.create(authenticationManager.authenticate(authenticationWithRawToken(TOKEN)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(JwtAuthenticationException.class);
                    assertThat(e).hasMessage("Invalid JWT token: bad token");
                })
                .verify();
    }

    @Test
    @DisplayName("authenticationConverter extracts token from a 'Bearer ' Authorization header")
    void converterExtractsTokenFromBearerHeader() {
        ServerAuthenticationConverter converter = authenticationManager.authenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any-endpoint").header("Authorization", "Bearer " + TOKEN).build());

        StepVerifier.create(converter.convert(exchange))
                .assertNext(authentication -> {
                    assertThat(authentication.getCredentials()).isEqualTo(TOKEN);
                    // pin: the converter also carries the raw token as principal
                    assertThat(authentication.getPrincipal()).isEqualTo(TOKEN);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticationConverter returns empty when the Authorization header is missing")
    void converterReturnsEmptyWithoutAuthorizationHeader() {
        ServerAuthenticationConverter converter = authenticationManager.authenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any-endpoint").build());

        StepVerifier.create(converter.convert(exchange))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticationConverter rejects lowercase 'bearer' scheme (case-sensitivity pin)")
    void converterRejectsLowercaseBearerScheme() {
        // Behavior pin: header.startsWith("Bearer ") is case-sensitive, so the
        // lowercase scheme is not recognized and no authentication is produced.
        ServerAuthenticationConverter converter = authenticationManager.authenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any-endpoint").header("Authorization", "bearer tok").build());

        StepVerifier.create(converter.convert(exchange))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticationConverter rejects a non-Bearer scheme")
    void converterRejectsNonBearerScheme() {
        ServerAuthenticationConverter converter = authenticationManager.authenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any-endpoint").header("Authorization", "Token tok").build());

        StepVerifier.create(converter.convert(exchange))
                .expectNextCount(0)
                .verifyComplete();
    }
}
