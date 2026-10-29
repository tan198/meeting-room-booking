package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.entity.BookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID roomId,
        String roomName,
        int roomFloor,
        int roomCapacity,
        UUID organizerId,
        String organizerName,
        String organizerEmail,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime actualStartTime,
        OffsetDateTime actualEndTime,
        BookingStatus status,
        UUID cancelledBy,
        OffsetDateTime cancelledAt,
        String cancelReason,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getRoom().getId(),
                b.getRoom().getName(),
                b.getRoom().getFloor(),
                b.getRoom().getCapacity(),
                b.getOrganizer().getId(),
                b.getOrganizer().getFullName(),
                b.getOrganizer().getEmail(),
                b.getTitle(),
                b.getDescription(),
                b.getStartTime(),
                b.getEndTime(),
                b.getActualStartTime(),
                b.getActualEndTime(),
                b.getStatus(),
                b.getCancelledBy(),
                b.getCancelledAt(),
                b.getCancelReason(),
                b.getVersion(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}

