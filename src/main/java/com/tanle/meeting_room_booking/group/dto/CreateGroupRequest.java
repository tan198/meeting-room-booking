package com.tanle.meeting_room_booking.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank(message = "Mã dự án / phòng ban không được để trống")
        @Size(max = 50, message = "Mã tối đa 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chỉ chứa chữ, số, gạch ngang hoặc gạch dưới")
        String code,

        @NotBlank(message = "Tên dự án / phòng ban không được để trống")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String name,

        /** Loại Group do Admin tự nhập (VD: "PROJECT", "DEPARTMENT"... mặc định là "PROJECT"). */
        @Size(max = 50, message = "Loại tối đa 50 ký tự")
        String type,

        @Size(max = 1000)
        String description,

        /** Danh sách nhân viên (từ các Team khác nhau) tham gia vào Dự án / Phòng ban này. */
        Set<UUID> memberUserIds
) {
}
