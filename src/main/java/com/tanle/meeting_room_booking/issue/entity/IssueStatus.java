package com.tanle.meeting_room_booking.issue.entity;

/**
 * Trạng thái xử lý sự cố phòng họp theo Spec mục 10.2:
 * OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (hoặc OPEN -> CLOSED, RESOLVED -> IN_PROGRESS).
 */
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}

