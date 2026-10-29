package com.tanle.meeting_room_booking.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank(message = "Tên vai trò không được để trống")
        @Size(max = 50, message = "Tên vai trò tối đa 50 ký tự")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Tên vai trò chỉ chứa chữ hoa, số và dấu gạch dưới (VD: ROOM_MANAGER)")
        String name,

        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description,

        Set<@NotBlank String> permissionCodes
) {
}

