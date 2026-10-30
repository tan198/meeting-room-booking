package com.tanle.meeting_room_booking.report.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UsageReportResponse(
        OffsetDateTime fromTime,
        OffsetDateTime toTime,
        OffsetDateTime generatedAt,
        int totalEvaluatedBookings,
        int currentlyInUseCount,
        int noShowCount,
        int noShowSampleSize,
        Double noShowRate,
        long scheduledApprovedMinutes,
        long effectiveAllocatedMinutes,
        long confirmedUsageMinutes,
        long inferredUsageMinutes,
        long earlyReleasedMinutes,
        Map<String, Long> earlyReleaseMinutesByReason,
        Map<String, Integer> usageStatusCounts,
        List<RoomUsageItem> roomUsageBreakdown
) {
    public record RoomUsageItem(
            UUID roomId,
            String roomCode,
            String roomName,
            int approvedBookingCount,
            int noShowCount,
            int interruptedCount,
            long scheduledApprovedMinutes,
            long effectiveAllocatedMinutes,
            long confirmedUsageMinutes,
            long inferredUsageMinutes,
            long earlyReleasedMinutes
    ) {}
}

