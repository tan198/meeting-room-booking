package com.tanle.meeting_room_booking.role.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.role.dto.CreateRoleRequest;
import com.tanle.meeting_room_booking.role.dto.RoleResponse;
import com.tanle.meeting_room_booking.role.dto.UpdateRoleRequest;
import com.tanle.meeting_room_booking.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "5. Roles & Permissions (RBAC)", description = "Quản lý động Vai trò (Role) và Quyền hạn (Permission)")
public class RoleController {

    private final RoleService roleService;

    @Operation(
            summary = "Danh sách tất cả vai trò (Role) và các quyền đi kèm",
            description = "Yêu cầu quyền `ROLE_MANAGE`."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<List<RoleResponse>> getAll() {
        return ApiResponse.ok(roleService.findAll());
    }

    @Operation(summary = "Xem chi tiết một vai trò theo ID", description = "Yêu cầu quyền `ROLE_MANAGE`.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<RoleResponse> getById(@Parameter(description = "UUID của vai trò") @PathVariable UUID id) {
        return ApiResponse.ok(roleService.getById(id));
    }

    @Operation(
            summary = "Tạo vai trò (Role) mới và gán danh sách Permission",
            description = "Cho phép tạo vai trò tùy chỉnh (VD: `ROOM_MANAGER`, `RECEPTIONIST`) với tập quyền (`permissionCodes`) bất kỳ. Yêu cầu quyền `ROLE_MANAGE`."
    )
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo vai trò thành công", roleService.createRole(request)));
    }

    @Operation(
            summary = "Cập nhật mô tả và danh sách Permission của vai trò",
            description = "Cho phép thêm/bớt quyền của bất kỳ vai trò nào (trừ `ADMIN`). Thay đổi có hiệu lực ngay lập tức ở request tiếp theo của các user thuộc vai trò đó."
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<RoleResponse> update(
            @Parameter(description = "UUID của vai trò") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Cập nhật vai trò thành công", roleService.updateRole(id, request));
    }

    @Operation(
            summary = "Xóa vai trò tùy chỉnh",
            description = "Chỉ cho phép xóa vai trò không phải mặc định (`ADMIN`, `USER`) và chưa có người dùng nào đang được gán."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<Void> delete(@Parameter(description = "UUID của vai trò") @PathVariable UUID id) {
        roleService.deleteRole(id);
        return ApiResponse.ok("Xóa vai trò thành công");
    }
}

