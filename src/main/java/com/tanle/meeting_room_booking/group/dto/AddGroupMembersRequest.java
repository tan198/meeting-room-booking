package com.tanle.meeting_room_booking.group.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record AddGroupMembersRequest(
        @NotEmpty(message = "Danh sách userId không được để trống")
        Set<UUID> userIds
) {
}

