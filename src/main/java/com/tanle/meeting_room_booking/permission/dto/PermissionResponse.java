package com.tanle.meeting_room_booking.permission.dto;

import com.tanle.meeting_room_booking.permission.entity.Permission;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String description
) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getCode(), p.getDescription());
    }
}

