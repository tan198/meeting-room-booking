package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import com.tanle.meeting_room_booking.issue.entity.RoomIssueHistory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomIssueHistoryResponse(
        UUID id,
        UUID issueId,
        UUID actorId,
        IssueStatus fromStatus,
        IssueStatus toStatus,
        String reason,
        String resolution,
        OffsetDateTime createdAt
) {
    public static RoomIssueHistoryResponse from(RoomIssueHistory h) {
        return new RoomIssueHistoryResponse(
                h.getId(),
                h.getIssueId(),
                h.getActorId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getReason(),
                h.getResolution(),
                h.getCreatedAt()
        );
    }
}

