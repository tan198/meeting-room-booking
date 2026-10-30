package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.issue.entity.IssueImpact;
import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu chuyển trạng thái xử lý báo cáo sự cố phòng họp (Spec mục 10.2)")
public record TransitionRoomIssueRequest(

        @Schema(description = "Phiên bản hiện tại của báo cáo sự cố (bắt buộc để kiểm soát đồng thời)", example = "0")
        Long expectedVersion,

        @Schema(description = "Trạng thái đích (`IN_PROGRESS`, `RESOLVED`, `CLOSED`)", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Trạng thái đích (targetStatus) là bắt buộc")
        IssueStatus targetStatus,

        @Schema(description = "Mức độ ảnh hưởng thực tế do Quản trị viên xác nhận (`DEGRADED` hoặc `UNUSABLE`)", example = "DEGRADED")
        IssueImpact actualImpact,

        @Schema(description = "Ghi chú cách khắc phục (bắt buộc khi chuyển `IN_PROGRESS -> RESOLVED`)", example = "Đã thay dây HDMI mới và cân chỉnh máy chiếu")
        @Size(max = 2000, message = "Nội dung khắc phục tối đa 2000 ký tự")
        String resolution,

        @Schema(description = "Lý do chuyển trạng thái (bắt buộc khi đóng trực tiếp `OPEN -> CLOSED` hoặc mở lại `RESOLVED -> IN_PROGRESS`)", example = "Báo trùng với sự cố trước đó")
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
        String reason
) {
}

