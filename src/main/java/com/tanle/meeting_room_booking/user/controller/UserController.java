package com.tanle.meeting_room_booking.user.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.response.UserResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.user.dto.ChangePassword;
import com.tanle.meeting_room_booking.user.dto.CreateUser;
import com.tanle.meeting_room_booking.user.dto.UpdateProfile;
import com.tanle.meeting_room_booking.user.dto.UpdateUser;
import com.tanle.meeting_room_booking.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(userService.getById(principal.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyProfile(@AuthenticationPrincipal AuthUser principal,
                                                     @Valid @RequestBody UpdateProfile request) {
        return ApiResponse.ok("Cập nhật hồ sơ thành công", userService.updateProfile(principal.id(), request));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(@AuthenticationPrincipal AuthUser principal,
                                              @Valid @RequestBody ChangePassword request) {
        userService.changePassword(principal.id(), request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    // ----- Admin -----

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUser request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo người dùng thành công", userService.createUser(request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ApiResponse<PageResponse<UserResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.search(keyword, roleName, isActive, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal AuthUser principal,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateUser request) {
        return ApiResponse.ok("Cập nhật người dùng thành công", userService.updateUser(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        userService.deleteUser(principal.id(), id);
        return ApiResponse.ok("Xóa người dùng thành công");
    }
}
