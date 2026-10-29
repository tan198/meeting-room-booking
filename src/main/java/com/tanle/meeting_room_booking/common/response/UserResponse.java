package com.tanle.meeting_room_booking.common.response;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.user.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String roleName,
        List<String> permissions,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserResponse from(User user) {
        List<String> perms = user.getRole() != null && user.getRole().getPermissions() != null
                ? user.getRole().getPermissions().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .toList()
                : List.of();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                perms,
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
