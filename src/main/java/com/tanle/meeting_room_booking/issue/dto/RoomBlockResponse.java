package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.issue.entity.RoomBlock;
import com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomBlockResponse(
        UUID id,
        UUID roomId,
        String roomCode,
        String roomName,
        UUID issueId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String reason,
        RoomBlockStatus status,
        UUID createdBy,
        UUID cancelledBy,
        OffsetDateTime cancelledAt,
        String cancelReason,
        UUID replacedById,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RoomBlockResponse from(RoomBlock rb) {
        return new RoomBlockResponse(
                rb.getId(),
                rb.getRoom().getId(),
                rb.getRoom().getCode(),
                rb.getRoom().getName(),
                rb.getIssue() != null ? rb.getIssue().getId() : null,
                rb.getStartTime(),
                rb.getEndTime(),
                rb.getReason(),
                rb.getStatus(),
                rb.getCreatedBy(),
                rb.getCancelledBy(),
                rb.getCancelledAt(),
                rb.getCancelReason(),
                rb.getReplacedById(),
                rb.getVersion(),
                rb.getCreatedAt(),
                rb.getUpdatedAt()
        );
    }
}

