package com.tanle.meeting_room_booking.auth.controller;

import com.tanle.meeting_room_booking.auth.dto.LoginRequest;
import com.tanle.meeting_room_booking.auth.dto.LoginResponse;
import com.tanle.meeting_room_booking.auth.dto.RefreshRequest;
import com.tanle.meeting_room_booking.auth.service.AuthService;
import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.UserResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.user.service.UserService;
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
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Đăng nhập thành công", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Làm mới token thành công", authService.refresh(request.refreshToken()));
    }

    /** Revokes the given refresh token. The token itself is the credential, so no access token is required. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Đăng xuất thành công");
    }

    /** Revokes every refresh token of the current user (sign out of all devices). */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal AuthUser principal) {
        authService.logoutAll(principal.id());
        return ApiResponse.ok("Đã đăng xuất khỏi tất cả thiết bị");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(userService.getById(principal.id()));
    }
}
