package com.tanle.meeting_room_booking.auth.controller;

import com.tanle.meeting_room_booking.auth.dto.LoginRequest;
import com.tanle.meeting_room_booking.auth.dto.LoginResponse;
import com.tanle.meeting_room_booking.auth.dto.RefreshRequest;
import com.tanle.meeting_room_booking.auth.service.AuthService;
import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.UserResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Xác thực, làm mới token và quản lý phiên đăng nhập")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực bằng email và mật khẩu, trả về cặp Access Token (JWT) và Refresh Token."
    )
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Đăng nhập thành công", authService.login(request));
    }

    @Operation(
            summary = "Làm mới token (Refresh Token Rotation)",
            description = "Thu hồi refresh token hiện tại và cấp cặp Access Token + Refresh Token mới. "
                    + "Nếu phát hiện dùng lại refresh token đã bị thu hồi, toàn bộ phiên của người dùng sẽ bị hủy."
    )
    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Làm mới token thành công", authService.refresh(request.refreshToken()));
    }

    /** Revokes the given refresh token. The token itself is the credential, so no access token is required. */
    @Operation(
            summary = "Đăng xuất thiết bị hiện tại",
            description = "Thu hồi refresh token được truyền lên (không yêu cầu gửi kèm Access Token)."
    )
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Đăng xuất thành công");
    }

    /** Revokes every refresh token of the current user (sign out of all devices). */
    @Operation(
            summary = "Đăng xuất khỏi tất cả thiết bị",
            description = "Thu hồi toàn bộ refresh token đang hoạt động của người dùng hiện tại."
    )
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal) {
        authService.logoutAll(principal.id());
        return ApiResponse.ok("Đã đăng xuất khỏi tất cả thiết bị");
    }

    @Operation(
            summary = "Lấy thông tin người dùng hiện tại",
            description = "Trả về hồ sơ của người dùng tương ứng với Access Token đang đăng nhập."
    )
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(userService.getById(principal.id()));
    }
}
