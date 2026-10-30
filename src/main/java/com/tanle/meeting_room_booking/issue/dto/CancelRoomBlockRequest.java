package com.tanle.meeting_room_booking.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu hủy khoảng chặn phòng (RoomBlock Cancel - Spec mục 11.3)")
public record CancelRoomBlockRequest(

        @Schema(description = "Phiên bản hiện tại của RoomBlock (kiểm soát đồng thời)", example = "0")
        Long expectedVersion,

        @Schema(description = "Lý do hủy khoảng chặn phòng (bắt buộc)", example = "Đã khắc phục xong sự cố điều hòa sớm hơn dự kiến", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Lý do hủy khoảng chặn (reason) là bắt buộc")
        @Size(max = 1000, message = "Lý do hủy tối đa 1000 ký tự")
        String reason
) {
}

