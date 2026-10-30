package com.tanle.meeting_room_booking.issue.dto;

import com.tanle.meeting_room_booking.issue.entity.IssueCategory;
import com.tanle.meeting_room_booking.issue.entity.IssueImpact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Dữ liệu báo cáo sự cố phòng họp / thiết bị (Spec mục 10.1)")
public record CreateRoomIssueRequest(

        @Schema(description = "UUID của phòng họp gặp sự cố (phòng vẫn phải tồn tại; cho phép cả khi phòng đang INACTIVE)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phòng họp (roomId) là bắt buộc")
        UUID roomId,

        @Schema(description = "UUID của lịch họp có liên quan (tùy chọn)")
        UUID relatedBookingId,

        @Schema(description = "Phân loại sự cố (`EQUIPMENT`, `FACILITY`, `CLEANLINESS`, `SAFETY`, `OTHER`)", example = "EQUIPMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phân loại sự cố (category) là bắt buộc")
        IssueCategory category,

        @Schema(description = "Tiêu đề tóm tắt sự cố (tối đa 200 ký tự)", example = "Máy chiếu phòng A301 bị nhòe màu", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tiêu đề sự cố không được để trống")
        @Size(max = 200, message = "Tiêu đề sự cố tối đa 200 ký tự")
        String title,

        @Schema(description = "Mô tả chi tiết hiện trạng sự cố", example = "Cáp HDMI bị lỏng và bóng đèn máy chiếu nhấp nháy liên tục", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Mô tả sự cố không được để trống")
        @Size(max = 2000, message = "Mô tả sự cố tối đa 2000 ký tự")
        String description,

        @Schema(description = "Mức độ ảnh hưởng đề xuất (`DEGRADED` hoặc `UNUSABLE` - tương đương proposedImpact)", example = "DEGRADED")
        IssueImpact impact,

        @Schema(description = "Alias của impact theo Spec 10.1 (`DEGRADED` hoặc `UNUSABLE`)", example = "DEGRADED")
        IssueImpact proposedImpact
) {
    public IssueImpact resolvedProposedImpact() {
        if (proposedImpact != null) {
            return proposedImpact;
        }
        return impact != null ? impact : IssueImpact.DEGRADED;
    }
}

