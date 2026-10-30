package com.tanle.meeting_room_booking.announcement.dto;

import com.tanle.meeting_room_booking.announcement.entity.Announcement;
import com.tanle.meeting_room_booking.announcement.entity.AudienceType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String content,
        AudienceType audienceType,
        List<UUID> audienceIds,
        int recipientCount,
        String relatedType,
        UUID relatedId,
        UUID correctionOfId,
        UUID publishedBy,
        OffsetDateTime publishedAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(
                a.getId(),
                a.getTitle(),
                a.getContent(),
                a.getAudienceType(),
                a.getAudienceIds(),
                a.getRecipientCount(),
                a.getRelatedType(),
                a.getRelatedId(),
                a.getCorrectionOfId(),
                a.getPublishedBy(),
                a.getPublishedAt()
        );
    }
}

