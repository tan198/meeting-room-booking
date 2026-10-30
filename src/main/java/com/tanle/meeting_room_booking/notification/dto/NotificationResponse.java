package com.tanle.meeting_room_booking.notification.dto;

import com.tanle.meeting_room_booking.notification.entity.Notification;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID bookingId,
        String type,
        String title,
        String message,
        Map<String, Object> payload,
        boolean isRead,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getBookingId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getPayload() != null ? n.getPayload() : Map.of(),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}

