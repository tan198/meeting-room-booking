package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request dành cho Admin phê duyệt và phân bổ phòng chính thức cho một yêu cầu đặt phòng đang PENDING (Spec 8.3 & 23.2).
 */
public record ApproveBookingRequest(
        Long expectedVersion,

        @NotNull(message = "ID phòng họp được phân bổ (roomId) không được để trống")
        UUID roomId,

        @Size(max = 2000, message = "Ghi chú phê duyệt tối đa 2000 ký tự")
        String note
) {
}

