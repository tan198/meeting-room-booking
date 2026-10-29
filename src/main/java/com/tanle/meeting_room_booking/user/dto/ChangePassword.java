package com.tanle.meeting_room_booking.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePassword(
        @NotBlank String currentPassword,

        @NotBlank
        @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$",
                message = "Mật khẩu phải có chữ in hoa, chữ thường, số và ký tự đặc biệt; không chứa khoảng trắng"
        )
        String newPassword
) {
}
