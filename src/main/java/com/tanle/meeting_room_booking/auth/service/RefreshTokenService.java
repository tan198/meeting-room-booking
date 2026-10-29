package com.tanle.meeting_room_booking.auth.service;

import com.tanle.meeting_room_booking.auth.entity.RefreshToken;
import com.tanle.meeting_room_booking.auth.repository.RefreshTokenRepository;
import com.tanle.meeting_room_booking.common.config.JwtProperties;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens. Only the SHA-256 hash is stored.
 * Re-using an already revoked token is treated as theft: every token of that user is revoked.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public record IssuedRefreshToken(String token, long expiresInSeconds) {
    }

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.ttl = properties.refreshExpirationMs() != null
                ? Duration.ofMillis(properties.refreshExpirationMs())
                : DEFAULT_TTL;
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .build());
        return new IssuedRefreshToken(raw, ttl.toSeconds());
    }

    /**
     * Validates and revokes the token (rotation), returning the owning user id.
     * noRollbackFor: the "revoke everything" reaction to token reuse must be committed even though we throw.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UUID consume(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (repository.revokeIfActive(token.getId(), now) == 0) {
            log.warn("Phát hiện dùng lại refresh token đã bị thu hồi, thu hồi toàn bộ token của user {}", token.getUserId());
            repository.revokeAllByUserId(token.getUserId(), now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return token.getUserId();
    }

    /** Idempotent: unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeIfActive(t.getId(), OffsetDateTime.now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllByUserId(userId, OffsetDateTime.now());
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(OffsetDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.info("Đã xóa {} refresh token hết hạn", deleted);
        }
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
