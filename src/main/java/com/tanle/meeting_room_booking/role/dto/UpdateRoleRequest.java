package com.tanle.meeting_room_booking.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRoleRequest(
        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description,

        @NotNull(message = "Danh sách mã quyền không được null")
        Set<@NotBlank String> permissionCodes
) {
}

