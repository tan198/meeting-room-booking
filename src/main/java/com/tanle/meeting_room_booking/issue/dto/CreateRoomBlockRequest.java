package com.tanle.meeting_room_booking.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu tạo khoảng chặn phòng (RoomBlock) kèm phương án xử lý nguyên tử toàn bộ lịch bị ảnh hưởng (Spec mục 11.2)")
public record CreateRoomBlockRequest(

        @Schema(description = "UUID của phòng họp cần chặn", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phòng họp (roomId) là bắt buộc")
        UUID roomId,

        @Schema(description = "Thời điểm bắt đầu chặn (ISO-8601)")
        OffsetDateTime startTime,

        @Schema(description = "Alias của startTime theo Spec (`startAt`)")
        OffsetDateTime startAt,

        @Schema(description = "Thời điểm kết thúc chặn (ISO-8601)")
        OffsetDateTime endTime,

        @Schema(description = "Alias của endTime theo Spec (`endAt`)")
        OffsetDateTime endAt,

        @Schema(description = "Chặn khẩn cấp ngay lập tức (`immediate = true`)", example = "false")
        Boolean immediate,

        @Schema(description = "UUID của báo cáo sự cố liên quan (nếu tạo RoomBlock từ một RoomIssue)")
        UUID issueId,

        @Schema(description = "Lý do chặn phòng (bắt buộc)", example = "Bảo trì hệ thống điều hòa trung tâm phòng A301", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Lý do chặn phòng (reason) không được để trống")
        @Size(max = 1000, message = "Lý do chặn phòng tối đa 1000 ký tự")
        String reason,

        @Schema(description = "Phiên bản hiện tại của RoomBlock cũ (chỉ dùng khi gọi POST /room-blocks/{id}/replace)")
        Long expectedVersion,

        @Schema(description = "Danh sách phương án xử lý (`REASSIGN` hoặc `CANCEL`) cho từng booking đang chiếm chỗ trong khoảng chặn")
        @Valid
        List<BookingResolutionItem> resolutions
) {
    public OffsetDateTime resolvedStartTime() {
        return startAt != null ? startAt : startTime;
    }

    public OffsetDateTime resolvedEndTime() {
        return endAt != null ? endAt : endTime;
    }

    public boolean isImmediate() {
        return Boolean.TRUE.equals(immediate);
    }

    public record BookingResolutionItem(
            @Schema(description = "UUID của booking bị ảnh hưởng", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "bookingId trong phương án xử lý là bắt buộc")
            UUID bookingId,

            @Schema(description = "Phiên bản dự kiến của booking tại thời điểm preview (dùng để phát hiện IMPACT_PLAN_STALE)")
            Long expectedVersion,

            @Schema(description = "Hành động xử lý: `REASSIGN` (chuyển sang phòng khác) hoặc `CANCEL` (hủy do vận hành)", example = "REASSIGN", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Hành động xử lý (action: REASSIGN hoặc CANCEL) là bắt buộc")
            String action,

            @Schema(description = "UUID phòng mới (bắt buộc nếu action = REASSIGN)")
            UUID roomId,

            @Schema(description = "Alias của roomId (targetRoomId)")
            UUID targetRoomId,

            @Schema(description = "Lý do chuyển phòng hoặc hủy lịch (nếu để trống sẽ kế thừa reason của RoomBlock)")
            String reason
    ) {
        public UUID resolvedTargetRoomId() {
            return targetRoomId != null ? targetRoomId : roomId;
        }
    }
}

