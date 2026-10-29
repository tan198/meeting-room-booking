package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        Long expectedVersion,

        @Size(max = 2000, message = "Lý do hủy tối đa 2000 ký tự")
        String reason
) {
}
