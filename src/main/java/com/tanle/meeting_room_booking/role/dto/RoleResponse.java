package com.tanle.meeting_room_booking.role.dto;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.role.entity.Role;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        List<String> permissions
) {
    public static RoleResponse from(Role role) {
        List<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                permissionCodes
        );
    }
}

