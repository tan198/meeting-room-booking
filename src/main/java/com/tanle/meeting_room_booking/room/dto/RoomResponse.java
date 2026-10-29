package com.tanle.meeting_room_booking.room.dto;

import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        int floor,
        int capacity,
        List<String> facilities,
        RoomStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getFloor(),
                room.getCapacity(),
                room.getFacilities() != null ? List.copyOf(room.getFacilities()) : List.of(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}

