package com.tanle.meeting_room_booking.announcement.dto;

import com.tanle.meeting_room_booking.announcement.entity.AudienceType;

import java.util.List;
import java.util.UUID;

public record AnnouncementPreviewResponse(
        AudienceType audienceType,
        List<UUID> audienceIds,
        int activeRecipientCount
) {
}

