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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "2. Users", description = "Quản lý hồ sơ cá nhân và quản trị người dùng (Admin)")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Xem hồ sơ cá nhân", description = "Trả về thông tin chi tiết của người dùng đang đăng nhập.")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(userService.getById(principal.id()));
    }

    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Cho phép người dùng tự cập nhật họ tên của mình.")
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyProfile(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
                                                     @Valid @RequestBody UpdateProfile request) {
        return ApiResponse.ok("Cập nhật hồ sơ thành công", userService.updateProfile(principal.id(), request));
    }

    @Operation(
            summary = "Đổi mật khẩu cá nhân",
            description = "Đổi mật khẩu khi biết mật khẩu hiện tại. Sau khi đổi thành công, toàn bộ refresh token cũ sẽ bị thu hồi."
    )
    @PutMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
                                              @Valid @RequestBody ChangePassword request) {
        userService.changePassword(principal.id(), request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    // ----- Admin -----

    @Operation(summary = "[Admin] Tạo người dùng mới", description = "Yêu cầu quyền `USER_CREATE`.")
    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUser request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo người dùng thành công", userService.createUser(request)));
    }

    @Operation(
            summary = "[Admin] Tìm kiếm & phân trang người dùng",
            description = "Yêu cầu quyền `USER_READ`. Hỗ trợ lọc theo từ khóa (họ tên/email), vai trò và trạng thái hoạt động."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ApiResponse<PageResponse<UserResponse>> search(
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc email") @RequestParam(required = false) String keyword,
            @Parameter(description = "Tên vai trò (VD: ADMIN, USER)") @RequestParam(required = false) String roleName,
            @Parameter(description = "Trạng thái hoạt động (true/false)") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 50)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.search(keyword, roleName, isActive, page, size));
    }

    @Operation(summary = "[Admin] Xem chi tiết người dùng theo ID", description = "Yêu cầu quyền `USER_READ`.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ApiResponse<UserResponse> getById(@Parameter(description = "UUID của người dùng") @PathVariable UUID id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @Operation(
            summary = "[Admin] Cập nhật thông tin người dùng",
            description = "Yêu cầu quyền `USER_UPDATE`. Admin không thể tự khóa hoặc tự hạ quyền của chính mình."
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> update(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
                                            @Parameter(description = "UUID của người dùng") @PathVariable UUID id,
                                            @Valid @RequestBody UpdateUser request) {
        return ApiResponse.ok("Cập nhật người dùng thành công", userService.updateUser(principal.id(), id, request));
    }

    @Operation(
            summary = "[Admin] Xóa mềm người dùng",
            description = "Yêu cầu quyền `USER_DELETE`. Đánh dấu xóa mềm, vô hiệu hóa tài khoản và thu hồi toàn bộ refresh token."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ApiResponse<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
                                    @Parameter(description = "UUID của người dùng") @PathVariable UUID id) {
        userService.deleteUser(principal.id(), id);
        return ApiResponse.ok("Xóa người dùng thành công");
    }
}
