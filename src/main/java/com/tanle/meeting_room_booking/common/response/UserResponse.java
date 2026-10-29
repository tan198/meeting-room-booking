package com.tanle.meeting_room_booking.common.response;

import com.tanle.meeting_room_booking.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String roleName,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserResponse from (User user){
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
