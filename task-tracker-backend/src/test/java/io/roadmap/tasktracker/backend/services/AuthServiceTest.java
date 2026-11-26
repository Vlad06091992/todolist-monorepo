package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.dto.AuthRequest;
import io.roadmap.tasktracker.backend.dto.UserCreateRequest;
import io.roadmap.tasktracker.backend.entities.User;
import io.roadmap.tasktracker.backend.exceptions.UserAlreadyExistsException;
import io.roadmap.tasktracker.backend.kafka.KafkaEventPublisher;
import io.roadmap.tasktracker.backend.mappers.UserMapper;
import io.roadmap.tasktracker.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mock-based unit tests for {@link AuthService}. No Spring context, no database.
 * All collaborators are mocks; the only real dependency is the scheduler, which is
 * Schedulers.immediate() so everything runs deterministically on the subscriber thread.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Exact message from the AuthService source; used for BOTH the wrong-password
    // and the unknown-login paths (anti-enumeration).
    private static final String BAD_CREDENTIALS_MESSAGE = "Invalid login or password";

    private static final String LOGIN = "alice";
    private static final String RAW_PASSWORD = "raw-password";
    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Built in @BeforeEach (not as a field initializer) so the Mockito-injected
        // mocks are already available. The scheduler must be a real instance:
        // subscribeOn() with a mock Scheduler would fail at subscribe time.
        authService = new AuthService(jwtTokenProvider, Schedulers.immediate(), userService,
                passwordEncoder, userMapper, kafkaEventPublisher, transactionTemplate);
    }

    private static User userWithHash(String passwordHash) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setLogin(LOGIN);
        user.setPassword(passwordHash);
        user.setEmail(LOGIN + "@example.com");
        return user;
    }

    @Test
    @DisplayName("login with correct credentials returns the generated JWT and calls generateToken with the login")
    void loginCorrectCredentialsReturnsGeneratedToken() {
        AuthRequest request = new AuthRequest(LOGIN, RAW_PASSWORD);
        User user = userWithHash(BCRYPT_HASH);
        when(userService.findByLogin(LOGIN)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);
        when(jwtTokenProvider.generateToken(LOGIN)).thenReturn("jwt-token");

        StepVerifier.create(authService.login(request))
                .expectNext("jwt-token")
                .verifyComplete();

        verify(jwtTokenProvider).generateToken(LOGIN);
    }

    @Test
    @DisplayName("login with wrong password fails with the exact BadCredentialsException message from the source")
    void loginWrongPasswordFailsWithExactMessage() {
        AuthRequest request = new AuthRequest(LOGIN, RAW_PASSWORD);
        User user = userWithHash(BCRYPT_HASH);
        when(userService.findByLogin(LOGIN)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(false);

        StepVerifier.create(authService.login(request))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BadCredentialsException.class);
                    assertThat(e).hasMessage(BAD_CREDENTIALS_MESSAGE);
                })
                .verify();

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("unknown login reports the same message as a wrong password (anti-enumeration pin)")
    void unknownLoginReportsSameMessageAsWrongPassword() {
        // Two consecutive subscriptions: the first exercises the existing-user +
        // wrong-password path, the second the unknown-user path. Both must surface
        // the identical message so callers cannot enumerate logins by the error.
        AuthRequest request = new AuthRequest(LOGIN, RAW_PASSWORD);
        User user = userWithHash(BCRYPT_HASH);
        when(userService.findByLogin(LOGIN)).thenReturn(Mono.just(user), Mono.empty());
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(false);

        AtomicReference<Throwable> wrongPasswordError = new AtomicReference<>();
        StepVerifier.create(authService.login(request))
                .expectErrorSatisfies(wrongPasswordError::set)
                .verify();

        AtomicReference<Throwable> unknownUserError = new AtomicReference<>();
        StepVerifier.create(authService.login(request))
                .expectErrorSatisfies(unknownUserError::set)
                .verify();

        assertThat(wrongPasswordError.get())
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(BAD_CREDENTIALS_MESSAGE);
        assertThat(unknownUserError.get())
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(BAD_CREDENTIALS_MESSAGE);
        assertThat(unknownUserError.get().getMessage()).isEqualTo(wrongPasswordError.get().getMessage());
    }

    @Test
    @DisplayName("unknown login never touches the password encoder (timing side-channel documentation)")
    void unknownLoginNeverChecksPassword() {
        AuthRequest request = new AuthRequest(LOGIN, RAW_PASSWORD);
        when(userService.findByLogin(LOGIN)).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(request))
                .expectError(BadCredentialsException.class)
                .verify();

        verifyNoInteractions(passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("register maps the request and delegates to userService.registerUser")
    void registerDelegatesToUserServiceWithMappedEntity() {
        UserCreateRequest request = new UserCreateRequest(LOGIN, LOGIN + "@example.com", RAW_PASSWORD);
        User mapped = userWithHash("encoded-password");
        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userService.registerUser(mapped)).thenReturn(Mono.just(mapped));

        StepVerifier.create(authService.register(request))
                .expectNext(mapped)
                .verifyComplete();

        verify(userService).registerUser(mapped);
    }

    @Test
    @DisplayName("register propagates UserAlreadyExistsException unchanged")
    void registerPropagatesUserAlreadyExistsExceptionUnchanged() {
        UserCreateRequest request = new UserCreateRequest(LOGIN, LOGIN + "@example.com", RAW_PASSWORD);
        User mapped = userWithHash("encoded-password");
        UserAlreadyExistsException failure = new UserAlreadyExistsException();
        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userService.registerUser(mapped)).thenReturn(Mono.error(failure));

        StepVerifier.create(authService.register(request))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(failure))
                .verify();
    }
}
