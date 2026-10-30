package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.booking.entity.BookingStatus;
import com.tanle.meeting_room_booking.booking.entity.MeetingType;
import com.tanle.meeting_room_booking.booking.entity.UsageStatus;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
import com.tanle.meeting_room_booking.room.dto.RoomResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomBlockPreviewResponse(
        UUID roomId,
        String roomCode,
        String roomName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int affectedBookingCount,
        List<AffectedBookingItem> affectedBookings
) {
    public record AffectedBookingItem(
            UUID bookingId,
            long version,
            String title,
            MeetingType meetingType,
            Visibility visibility,
            BookingStatus status,
            UsageStatus usageStatus,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            int attendeeCount,
            List<String> requiredEquipments,
            UUID organizerId,
            String organizerName,
            List<RoomResponse> suggestedRooms
    ) {}
}
