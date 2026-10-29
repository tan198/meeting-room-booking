package com.tanle.meeting_room_booking.auth.dto;

import com.tanle.meeting_room_booking.common.response.UserResponse;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user
) {
}
