package com.tanle.meeting_room_booking.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfile(
        @NotBlank @Size(max = 100) String fullName
) {
}
