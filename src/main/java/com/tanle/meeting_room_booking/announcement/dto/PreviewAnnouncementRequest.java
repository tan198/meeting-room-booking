package com.tanle.meeting_room_booking.announcement.dto;

import com.tanle.meeting_room_booking.announcement.entity.AudienceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu xem trước số lượng tài khoản ACTIVE nhận thông báo theo phạm vi (POST /api/v1/announcements/preview - Spec mục 22 UC-04 & 23.5)")
public record PreviewAnnouncementRequest(

        @Schema(description = "Cấu trúc phạm vi theo Spec 23.2 (`{ type, ids }`)")
        @Valid
        AnnouncementAudienceDto audience,

        @Schema(description = "Loại phạm vi truyền trực tiếp (`ALL`, `DEPARTMENTS`, `TEAMS`, `PROJECTS`, `BOOKINGS`)")
        AudienceType audienceType,

        @Schema(description = "Danh sách UUID phạm vi truyền trực tiếp")
        List<UUID> targetIds
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

