package com.tanle.meeting_room_booking.announcement.dto;

import com.tanle.meeting_room_booking.announcement.entity.AudienceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Phạm vi đối tượng nhận thông báo chung (Spec mục 12.3 & 23.2)")
public record AnnouncementAudienceDto(

        @Schema(description = "Loại phạm vi (`ALL`, `DEPARTMENTS`, `TEAMS`, `PROJECTS`, `BOOKINGS`)", example = "ALL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Loại phạm vi (audience.type) là bắt buộc")
        AudienceType type,

        @Schema(description = "Danh sách UUID tương ứng (để trống nếu type = ALL; bắt buộc có ít nhất 1 ID nếu khác ALL)")
        List<UUID> ids
) {
}

