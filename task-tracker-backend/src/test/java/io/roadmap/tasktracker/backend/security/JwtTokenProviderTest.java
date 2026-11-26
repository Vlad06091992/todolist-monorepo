package io.roadmap.tasktracker.backend.security;

import io.jsonwebtoken.Claims;
import io.roadmap.tasktracker.backend.exceptions.JwtAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link JwtTokenProvider} (no Spring context, no Mockito needed:
 * the class only depends on two @Value fields which are set via ReflectionTestUtils).
 *
 * Exception matrix pinned against jjwt 0.12.5 (verified in the jjwt sources):
 * - null / empty / whitespace-only token -> IllegalArgumentException (jjwt validates the
 *   raw input before any parsing; extractAllClaims catches only JwtException, so it escapes unwrapped)
 * - structurally malformed token (no separators, embedded whitespace) -> MalformedJwtException
 *   (a JwtException) -> wrapped into JwtAuthenticationException
 * - bad signature -> SignatureException (JwtException) -> wrapped
 * - expired -> ExpiredJwtException (JwtException) -> wrapped
 */
class JwtTokenProviderTest {

    // 48 decoded bytes -> satisfies the HS256 minimum key length (32 bytes)
    private static final String SECRET_A = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private static final String SECRET_B = Base64.getEncoder()
            .encodeToString("abcdef0123456789abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));

    private static final int EXPIRATION_HOURS = 24;
    private static final long TOLERANCE_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = providerWithSecret(SECRET_A, EXPIRATION_HOURS);
    }

    private static JwtTokenProvider providerWithSecret(String base64Secret, int expirationHours) {
        JwtTokenProvider tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "secretKey", base64Secret);
        ReflectionTestUtils.setField(tokenProvider, "expirationHours", expirationHours);
        return tokenProvider;
    }

    @Test
    @DisplayName("generateToken then extractLogin returns the original subject")
    void generateTokenThenExtractLoginReturnsOriginalSubject() {
        String token = provider.generateToken("alice");

        assertThat(provider.extractLogin(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("generated token expiration is within [now, now + configuredHours] with 30s tolerance")
    void expirationClaimIsWithinConfiguredWindow() {
        long now = System.currentTimeMillis();
        long expectedMax = now + TimeUnit.HOURS.toMillis(EXPIRATION_HOURS);

        String token = provider.generateToken("alice");
        Date expiration = provider.extractClaim(token, Claims::getExpiration);

        assertThat(expiration.getTime()).isBetween(now - TOLERANCE_MILLIS, expectedMax + TOLERANCE_MILLIS);
    }

    @Test
    @DisplayName("token signed with a different secret is rejected")
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = provider.generateToken("alice");
        JwtTokenProvider otherProvider = providerWithSecret(SECRET_B, EXPIRATION_HOURS);

        assertThatThrownBy(() -> otherProvider.extractLogin(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageStartingWith("Invalid JWT token: ");
    }

    @Test
    @DisplayName("tampered payload segment is rejected")
    void tamperedPayloadIsRejected() {
        String token = provider.generateToken("alice");
        String tampered = tamperPayload(token);

        assertThatThrownBy(() -> provider.extractLogin(tampered))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageStartingWith("Invalid JWT token: ");
    }

    // Flips one bit inside the payload segment, so the original signature no longer
    // matches the mutated payload and jjwt fails signature verification.
    private static String tamperPayload(String token) {
        String[] segments = token.split("\\.");
        assertThat(segments).hasSize(3);
        byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
        payload[payload.length / 2] ^= 0x01;
        return segments[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                + "." + segments[2];
    }

    @Test
    @DisplayName("already expired token (negative expiration-hours) is rejected")
    void expiredTokenIsRejected() {
        // expirationHours is an int field, so a negative value is accepted and
        // produces a token whose exp claim lies in the past.
        JwtTokenProvider expiredProvider = providerWithSecret(SECRET_A, -1);
        String token = expiredProvider.generateToken("alice");

        assertThatThrownBy(() -> expiredProvider.extractLogin(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageStartingWith("Invalid JWT token: ");
    }

    @Test
    @DisplayName("structurally malformed token (no separators) is rejected as JwtAuthenticationException")
    void malformedTokenIsRejected() {
        // Content without separators reaches jjwt's tokenizer, which throws
        // MalformedJwtException (a JwtException) -> wrapped by extractAllClaims.
        assertThatThrownBy(() -> provider.extractLogin("this-is-not-a-jwt"))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageStartingWith("Invalid JWT token: ");
    }

    @Test
    @DisplayName("null, empty and whitespace-only tokens escape unwrapped as IllegalArgumentException")
    void nullEmptyAndBlankTokensEscapeAsIllegalArgumentException() {
        // documents current behavior: IAE escapes unwrapped.
        // extractAllClaims catches only JwtException; jjwt 0.12.5 validates the raw
        // input ("null or empty or only whitespace", see JwtParser Javadoc) with a plain
        // IllegalArgumentException BEFORE any JwtException can arise, so blank inputs
        // never reach the wrapping code.
        assertThatThrownBy(() -> provider.extractLogin(null))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(JwtAuthenticationException.class);

        assertThatThrownBy(() -> provider.extractLogin(""))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(JwtAuthenticationException.class);

        assertThatThrownBy(() -> provider.extractLogin(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    @DisplayName("two generateToken calls for the same login produce different strings")
    void twoTokensForSameLoginDiffer() {
        String first = provider.generateToken("alice");
        String second = first;

        // jjwt serializes iat/exp with second precision, so a call within the same
        // wall-clock second yields an identical token. Regenerate until the second
        // ticks over; bounded busy-wait (no sleeps), always terminates within ~1s.
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (second.equals(first) && System.nanoTime() < deadlineNanos) {
            second = provider.generateToken("alice");
        }

        assertThat(second).isNotEqualTo(first);
    }
}
