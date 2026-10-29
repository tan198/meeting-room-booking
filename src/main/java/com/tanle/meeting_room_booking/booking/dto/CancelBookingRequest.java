package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @Size(max = 255, message = "Lý do hủy tối đa 255 ký tự")
        String reason
) {
}

