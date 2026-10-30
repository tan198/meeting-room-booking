package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request dành cho Admin từ chối một yêu cầu đặt phòng đang PENDING (Spec 8.4 & 23.2).
 */
public record RejectBookingRequest(
        Long expectedVersion,

        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 2000, message = "Lý do từ chối tối đa 2000 ký tự")
        String reason
) {
}

