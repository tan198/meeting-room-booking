package com.tanle.meeting_room_booking.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tanle.meeting_room_booking.common.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String ISSUER = "meeting-room-booking";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Duration expiration;

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public  JwtTokenProvider(JwtProperties jwtProperties) {
        String secret = Objects.requireNonNull(jwtProperties.secret(), "Secret không được để rỗng");
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32){
            throw new IllegalArgumentException("Độ dài của secret phải ít nhất là 32 bytes");
        }
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e){
            throw new IllegalStateException("JWT secret không hợp lệ", e);
        }
        this.expiration = jwtProperties.expirationMs() != null ? Duration.ofMillis(jwtProperties.expirationMs()) : Duration.ofHours(1);
    }

    public IssuedToken generateToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return new IssuedToken(jwt.serialize(), expiresAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("Không thể ký JWT", e);
        }
    }


    public Optional<UUID> validateAndGetUserId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || exp == null || exp.before(new Date())) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            log.debug("JWT không hợp lệ: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long getExpiresInSeconds() {
        return expiration.toSeconds();
    }
}
