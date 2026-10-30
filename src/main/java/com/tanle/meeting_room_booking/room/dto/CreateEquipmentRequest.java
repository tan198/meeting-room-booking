package com.tanle.meeting_room_booking.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dữ liệu tạo mới danh mục thiết bị phòng họp (Spec mục 6.2 & 21.4)")
public record CreateEquipmentRequest(

        @Schema(description = "Mã thiết bị duy nhất (tự chuẩn hóa chữ hoa, 1-50 ký tự)", example = "PROJECTOR_4K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Mã thiết bị (code) không được để trống")
        @Size(max = 50, message = "Mã thiết bị tối đa 50 ký tự")
        String code,

        @Schema(description = "Tên thiết bị (1-150 ký tự)", example = "Máy chiếu Laser 4K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tên thiết bị (name) không được để trống")
        @Size(max = 150, message = "Tên thiết bị tối đa 150 ký tự")
        String name,

        @Schema(description = "Mô tả thiết bị", example = "Máy chiếu 4K hỗ trợ kết nối không dây")
        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description
) {
}

