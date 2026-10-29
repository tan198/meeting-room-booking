package com.tanle.meeting_room_booking.auth.service;

import com.tanle.meeting_room_booking.auth.entity.RefreshToken;
import com.tanle.meeting_room_booking.auth.repository.RefreshTokenRepository;
import com.tanle.meeting_room_booking.common.config.JwtProperties;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, new JwtProperties("s", 1000L, 60_000L));
    }

    private RefreshToken stored(UUID userId, OffsetDateTime expiresAt) {
        return RefreshToken.builder().id(UUID.randomUUID()).userId(userId)
                .tokenHash(RefreshTokenService.hash("raw")).expiresAt(expiresAt).build();
    }

    @Test
    void issueStoresOnlyTheHashNotTheRawToken() {
        UUID userId = UUID.randomUUID();

        var issued = service.issue(userId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(issued.token());
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.token()));
        assertThat(issued.expiresInSeconds()).isEqualTo(60);
    }

    @Test
    void consumeUnknownTokenFails() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("raw"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void consumeExpiredTokenFailsWithoutRevoking() {
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored(UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(BusinessException.class);
        verify(repository, never()).revokeIfActive(any(), any());
    }

    @Test
    void consumeActiveTokenRevokesItAndReturnsUser() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = stored(userId, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(repository.revokeIfActive(any(), any())).thenReturn(1);

        assertThat(service.consume("raw")).isEqualTo(userId);
        verify(repository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void reusingARevokedTokenRevokesAllTokensOfTheUser() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = stored(userId, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(repository.revokeIfActive(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(BusinessException.class);
        verify(repository).revokeAllByUserId(any(), any());
    }

    @Test
    void revokeIgnoresUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("raw");

        verify(repository, never()).revokeIfActive(any(), any());
    }
}
