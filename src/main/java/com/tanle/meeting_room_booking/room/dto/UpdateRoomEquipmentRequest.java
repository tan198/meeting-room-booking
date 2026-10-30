package com.tanle.meeting_room_booking.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Dữ liệu cập nhật danh sách thiết bị của một phòng họp (PUT /api/v1/rooms/{id}/equipment - Spec mục 16)")
public record UpdateRoomEquipmentRequest(

        @Schema(description = "Danh sách mã thiết bị (hoặc tên thiết bị) gắn với phòng họp", example = "[\"PROJECTOR\", \"WHITEBOARD\", \"TV\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Danh sách thiết bị (equipments / facilities) là bắt buộc")
        List<String> equipments
) {
}

