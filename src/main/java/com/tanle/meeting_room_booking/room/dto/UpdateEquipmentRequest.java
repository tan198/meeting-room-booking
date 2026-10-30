package com.tanle.meeting_room_booking.room.dto;

import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Dữ liệu cập nhật danh mục thiết bị phòng họp (Spec mục 6.2 & 21.4)")
public record UpdateEquipmentRequest(

        @Schema(description = "Phiên bản hiện tại của thiết bị (kiểm soát đồng thời)", example = "0")
        Long expectedVersion,

        @Schema(description = "Tên thiết bị (1-150 ký tự)", example = "Máy chiếu Laser 4K")
        @Size(max = 150, message = "Tên thiết bị tối đa 150 ký tự")
        String name,

        @Schema(description = "Mô tả thiết bị")
        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @Schema(description = "Trạng thái thiết bị (`ACTIVE` hoặc `INACTIVE`)", example = "ACTIVE")
        RoomStatus status
) {
}

