package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request dành cho Admin chuyển phòng họp do vận hành / sự cố (Spec 11.2 & 16: POST /api/v1/bookings/{id}/reassign-room).
 */
public record ReassignRoomRequest(
        Long expectedVersion,

        @NotNull(message = "ID phòng mới (roomId) không được để trống")
        UUID roomId,

        @NotBlank(message = "Lý do chuyển phòng không được để trống")
        @Size(max = 2000, message = "Lý do chuyển phòng tối đa 2000 ký tự")
        String reason
) {
}

