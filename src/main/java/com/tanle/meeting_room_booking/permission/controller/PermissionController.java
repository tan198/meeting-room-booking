package com.tanle.meeting_room_booking.permission.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.permission.dto.PermissionResponse;
import com.tanle.meeting_room_booking.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "5. Roles & Permissions (RBAC)", description = "Quản lý động Vai trò (Role) và Quyền hạn (Permission)")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(
            summary = "Lấy danh sách toàn bộ quyền hạn (Permissions) trong hệ thống",
            description = "Yêu cầu quyền `ROLE_MANAGE`. Dùng để hiển thị danh sách checkbox quyền khi tạo/sửa Role."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<List<PermissionResponse>> getAll() {
        return ApiResponse.ok(permissionService.findAll());
    }
}

