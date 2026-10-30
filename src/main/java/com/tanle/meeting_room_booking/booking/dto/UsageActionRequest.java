package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * Request tùy chọn khi Check-in hoặc Check-out (Spec 25.7: expectedVersion và reason bắt buộc nếu Admin can thiệp vận hành).
 */
public record UsageActionRequest(
        Long expectedVersion,

        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự")
        String reason
) {
}

