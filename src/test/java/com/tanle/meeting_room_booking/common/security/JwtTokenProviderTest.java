package com.tanle.meeting_room_booking.common.security;

import com.tanle.meeting_room_booking.common.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static JwtTokenProvider provider(String secret, long expirationMs) {
        return new JwtTokenProvider(new JwtProperties(secret, expirationMs, 1000L));
    }

    @Test
    void generatedTokenIsValidAndCarriesUserId() {
        JwtTokenProvider provider = provider(SECRET, 60_000);
        UUID userId = UUID.randomUUID();

        var issued = provider.generateToken(userId, "a@b.com", "USER");

        assertThat(provider.validateAndGetUserId(issued.token())).contains(userId);
        assertThat(provider.getExpiresInSeconds()).isEqualTo(60);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = provider(SECRET, -1_000);

        var issued = provider.generateToken(UUID.randomUUID(), "a@b.com", "USER");

        assertThat(provider.validateAndGetUserId(issued.token())).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenProvider provider = provider(SECRET, 60_000);
        String token = provider.generateToken(UUID.randomUUID(), "a@b.com", "USER").token();

        String tampered = token.substring(0, token.length() - 12) + "AAAAAAAAAAAA";

        assertThat(provider.validateAndGetUserId(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = provider("another-secret-another-secret-another-1", 60_000)
                .generateToken(UUID.randomUUID(), "a@b.com", "USER").token();

        assertThat(provider(SECRET, 60_000).validateAndGetUserId(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        JwtTokenProvider provider = provider(SECRET, 60_000);

        assertThat(provider.validateAndGetUserId("not-a-jwt")).isEmpty();
        assertThat(provider.validateAndGetUserId("")).isEmpty();
    }

    @Test
    void secretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> provider("too-short", 60_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
