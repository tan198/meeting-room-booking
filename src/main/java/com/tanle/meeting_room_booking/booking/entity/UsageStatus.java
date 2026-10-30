package com.tanle.meeting_room_booking.booking.entity;

/**
 * Trạng thái sử dụng thực tế của cuộc họp (tách biệt với quyết định phê duyệt BookingStatus) theo Spec mục 25.2.
 */
public enum UsageStatus {
    NOT_APPLICABLE,
    NOT_CHECKED_IN,
    IN_USE,
    COMPLETED,
    NO_SHOW,
    INTERRUPTED
}

