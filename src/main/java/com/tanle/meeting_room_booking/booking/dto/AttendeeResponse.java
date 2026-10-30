package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.AttendeeRole;
import com.tanle.meeting_room_booking.booking.entity.BookingAttendee;
import com.tanle.meeting_room_booking.booking.entity.RsvpStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendeeResponse(
        UUID userId,
        String email,
        String fullName,
        AttendeeRole attendeeRole,
        RsvpStatus rsvpStatus,
        String declineReason,
        OffsetDateTime respondedAt
) {
    public static AttendeeResponse from(BookingAttendee ba) {
        return new AttendeeResponse(
                ba.getUser().getId(),
                ba.getUser().getEmail(),
                ba.getUser().getFullName(),
                ba.getAttendeeRole(),
                ba.getRsvpStatus(),
                ba.getDeclineReason(),
                ba.getRespondedAt()
        );
    }
}

