package com.tanle.meeting_room_booking.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank(message = "Mã team không được để trống")
        @Size(max = 50, message = "Mã team tối đa 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã team chỉ chứa chữ, số, gạch ngang hoặc gạch dưới")
        String code,

        @NotBlank(message = "Tên team không được để trống")
        @Size(max = 100, message = "Tên team tối đa 100 ký tự")
        String name,

        @Size(max = 50, message = "Phân loại team tối đa 50 ký tự")
        String type,

        @Size(max = 1000)
        String description
) {
}
