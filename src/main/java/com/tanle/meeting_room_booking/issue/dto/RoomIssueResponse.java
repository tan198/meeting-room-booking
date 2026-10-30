package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.issue.entity.IssueCategory;
import com.tanle.meeting_room_booking.issue.entity.IssueImpact;
import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import com.tanle.meeting_room_booking.issue.entity.RoomIssue;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomIssueResponse(
        UUID id,
        UUID roomId,
        String roomCode,
        String roomName,
        UUID reporterId,
        String reporterName,
        String reporterEmail,
        UUID relatedBookingId,
        IssueCategory category,
        String title,
        String description,
        IssueImpact proposedImpact,
        IssueImpact actualImpact,
        IssueStatus status,
        String resolution,
        UUID processedBy,
        OffsetDateTime processedAt,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RoomIssueResponse from(RoomIssue issue) {
        return new RoomIssueResponse(
                issue.getId(),
                issue.getRoom().getId(),
                issue.getRoom().getCode(),
                issue.getRoom().getName(),
                issue.getReporter().getId(),
                issue.getReporter().getFullName(),
                issue.getReporter().getEmail(),
                issue.getRelatedBookingId(),
                issue.getCategory(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getProposedImpact(),
                issue.getActualImpact(),
                issue.getStatus(),
                issue.getResolution(),
                issue.getProcessedBy(),
                issue.getProcessedAt(),
                issue.getVersion(),
                issue.getCreatedAt(),
                issue.getUpdatedAt()
        );
    }
}

