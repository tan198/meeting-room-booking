package com.tanle.meeting_room_booking.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Long expirationMs,
        Long refreshExpirationMs
) {
}
