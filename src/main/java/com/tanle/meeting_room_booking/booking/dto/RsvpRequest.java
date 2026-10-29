package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RsvpRequest(
        @NotNull(message = "Trạng thái phản hồi không được để trống (ACCEPTED hoặc DECLINED)")
        RsvpStatus status,

        @Size(max = 255, message = "Lý do từ chối tối đa 255 ký tự")
        String declineReason
) {
}

