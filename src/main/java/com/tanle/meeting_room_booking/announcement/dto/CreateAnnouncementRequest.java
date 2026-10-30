package com.tanle.meeting_room_booking.announcement.dto;

import com.tanle.meeting_room_booking.announcement.entity.AudienceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu phát hành thông báo chung của Quản trị viên (POST /api/v1/announcements - Spec mục 12.3 & 23.2)")
public record CreateAnnouncementRequest(

        @Schema(description = "Tiêu đề thông báo chung (tối đa 200 ký tự)", example = "Thông báo bảo trì định kỳ hệ thống điện tầng 3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tiêu đề thông báo (title) không được để trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Schema(description = "Nội dung chi tiết thông báo (tối đa 2000 ký tự)", example = "Tầng 3 sẽ tiến hành kiểm tra hệ thống chiếu sáng từ 18:00 đến 20:00 hôm nay.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nội dung thông báo (content) không được để trống")
        @Size(max = 2000, message = "Nội dung thông báo tối đa 2000 ký tự")
        String content,

        @Schema(description = "Cấu trúc phạm vi người nhận theo Spec 23.2 (`{ type, ids }`)")
        @Valid
        AnnouncementAudienceDto audience,

        @Schema(description = "Alias loại phạm vi (`ALL`, `DEPARTMENTS`, `TEAMS`, `PROJECTS`, `BOOKINGS`)")
        AudienceType audienceType,

        @Schema(description = "Alias danh sách UUID phạm vi")
        List<UUID> targetIds,

        @Schema(description = "Loại đối tượng liên quan tùy chọn (ví dụ: `ROOM`, `ROOM_BLOCK`, `ROOM_ISSUE`)")
        String relatedType,

        @Schema(description = "UUID của đối tượng liên quan tùy chọn")
        UUID relatedId,

        @Schema(description = "UUID của thông báo chung cũ nếu đây là bản đính chính (Spec 12.3)")
        UUID correctionOfId
) {
    public AudienceType resolvedType() {
        if (audience != null && audience.type() != null) {
            return audience.type();
        }
        return audienceType;
    }

    public List<UUID> resolvedIds() {
        if (audience != null && audience.ids() != null) {
            return audience.ids();
        }
        return targetIds != null ? targetIds : List.of();
    }
}

