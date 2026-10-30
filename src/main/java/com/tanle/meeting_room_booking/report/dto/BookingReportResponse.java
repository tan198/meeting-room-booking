package com.tanle.meeting_room_booking.report.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record BookingReportResponse(
        OffsetDateTime fromTime,
        OffsetDateTime toTime,
        OffsetDateTime generatedAt,
        int totalBookings,
        Map<String, Integer> statusCounts,
        int noShowCount,
        long scheduledApprovedMinutes,
        long effectiveAllocatedMinutes,
        long confirmedUsageMinutes,
        long inferredUsageMinutes,
        long earlyReleasedMinutes,
        List<GroupMetricItem> byRoom,
        List<GroupMetricItem> byMeetingType,
        List<GroupMetricItem> byTeam,
        List<GroupMetricItem> byProject,
        List<GroupMetricItem> byDepartment
) {
    public record GroupMetricItem(
            String key,
            String name,
            int bookingCount,
            int approvedCount,
            int cancelledCount,
            int noShowCount,
            long scheduledApprovedMinutes,
            long effectiveAllocatedMinutes,
            long confirmedUsageMinutes,
            long inferredUsageMinutes
    ) {}
}

