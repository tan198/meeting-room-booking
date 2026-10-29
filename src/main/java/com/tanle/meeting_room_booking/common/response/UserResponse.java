package com.tanle.meeting_room_booking.user.dto;

import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.entity.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserResponse from (User user){
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
