package com.tanle.meeting_room_booking.room.dto;

import com.tanle.meeting_room_booking.room.entity.Equipment;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EquipmentResponse(
        UUID id,
        String code,
        String name,
        String description,
        RoomStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static EquipmentResponse from(Equipment eq) {
        return new EquipmentResponse(
                eq.getId(),
                eq.getCode(),
                eq.getName(),
                eq.getDescription(),
                eq.getStatus(),
                eq.getVersion(),
                eq.getCreatedAt(),
                eq.getUpdatedAt()
        );
    }
}

