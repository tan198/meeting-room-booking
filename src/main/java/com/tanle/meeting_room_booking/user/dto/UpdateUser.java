package com.tanle.meeting_room_booking.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateUser(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        String roleName,

        @NotNull
        Boolean isActive,

        UUID teamId,

        @Size(max = 50)
        String teamRole,

        @Valid
        List<CreateUser.TeamAssignmentDto> teams
) {
}
