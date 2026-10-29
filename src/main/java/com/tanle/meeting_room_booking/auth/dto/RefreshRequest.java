package com.tanle.meeting_room_booking.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
}
