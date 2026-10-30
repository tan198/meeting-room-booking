package com.tanle.meeting_room_booking.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Yêu cầu xem trước tác động khi tạo khoảng chặn phòng (RoomBlock Preview - Spec mục 11.2)")
public record PreviewRoomBlockRequest(

        @Schema(description = "UUID của phòng họp dự kiến chặn", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phòng họp (roomId) là bắt buộc")
        UUID roomId,

        @Schema(description = "Thời điểm bắt đầu chặn (ISO-8601). Nếu immediate = true và không truyền startTime thì lấy thời điểm hiện tại.")
        OffsetDateTime startTime,

        @Schema(description = "Alias của startTime theo Spec (`startAt`)")
        OffsetDateTime startAt,

        @Schema(description = "Thời điểm kết thúc chặn (ISO-8601)")
        OffsetDateTime endTime,

        @Schema(description = "Alias của endTime theo Spec (`endAt`)")
        OffsetDateTime endAt,

        @Schema(description = "Chặn khẩn cấp ngay lập tức (`immediate = true` cho phép bắt đầu ngay tại thời điểm hiện tại mà không cần căn mốc 15 phút)", example = "false")
        Boolean immediate,

        @Schema(description = "UUID của báo cáo sự cố liên quan (nếu có)")
        UUID issueId
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
}
