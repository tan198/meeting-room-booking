package com.tanle.meeting_room_booking.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateUser(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$",
                message = "Mật khẩu phải có chữ in hoa, chữ thường, số và ký tự đặc biệt; không chứa khoảng trắng"
        )
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Vai trò hệ thống (RBAC) không được để trống")
        String roleName,

        /** Tích hợp nhanh 1 Team do Admin đã tạo khi tạo User (tùy chọn). */
        UUID teamId,

        /** Vai trò trong Team do Admin tự đặt (VD: "Dev", "PM", "BA", "Leader"...). */
        @Size(max = 50)
        String teamRole,

        /** Hoặc truyền danh sách nhiều Team kèm vai trò (tùy chọn). */
        @Valid
        List<TeamAssignmentDto> teams
) {
    public record TeamAssignmentDto(
            @NotNull(message = "teamId không được để trống")
            UUID teamId,

            @Size(max = 50, message = "memberRole tối đa 50 ký tự")
            String memberRole
    ) {
    }
}
