package com.tanle.meeting_room_booking.booking.entity;

/**
 * Trạng thái của Đề nghị thay đổi lịch đã duyệt (BookingChange) theo Spec mục 9.
 */
public enum BookingChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}

