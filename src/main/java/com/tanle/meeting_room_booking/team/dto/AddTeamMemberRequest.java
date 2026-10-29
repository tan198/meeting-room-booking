package com.tanle.meeting_room_booking.team.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull(message = "userId không được để trống")
        UUID userId,

        @Size(max = 50, message = "Vai trò trong team tối đa 50 ký tự (VD: Dev, PM, BA, Leader...)")
        String memberRole
) {
}
