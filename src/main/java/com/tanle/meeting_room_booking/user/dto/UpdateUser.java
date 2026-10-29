package com.tanle.meeting_room_booking.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUser(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        String roleName,

        @NotNull
        Boolean isActive
) {
}
