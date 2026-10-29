package com.tanle.meeting_room_booking.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateBookingRequest(
        UUID roomId,

        @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
        @Size(max = 200, message = "Tiêu đề cuộc họp tối đa 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        OffsetDateTime startTime,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        OffsetDateTime endTime,

        @Min(value = 1, message = "Số người tham dự phải lớn hơn 0")
        Integer attendeeCount
) {
}

