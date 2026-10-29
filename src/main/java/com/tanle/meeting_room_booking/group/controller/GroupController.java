package com.tanle.meeting_room_booking.group.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.group.dto.AddGroupMembersRequest;
import com.tanle.meeting_room_booking.group.dto.CreateGroupRequest;
import com.tanle.meeting_room_booking.group.dto.GroupResponse;
import com.tanle.meeting_room_booking.group.service.GroupService;
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
@RequestMapping({"/api/v1/groups", "/api/v1/projects", "/api/v1/departments"})
@RequiredArgsConstructor
@Tag(name = "7. Groups (Projects / Departments)", description = "Quản lý Dự án (/projects), Phòng ban (/departments) và Nhóm (/groups) theo Spec 16")
public class GroupController {

    private final GroupService groupService;

    @Operation(
            summary = "Danh sách các Group (Dự án / Phòng ban) kèm thành viên",
            description = "Có thể lọc theo `type` (VD: PROJECT, DEPARTMENT...)."
    )
    @GetMapping
    public ApiResponse<List<GroupResponse>> getAll(
            @Parameter(description = "Lọc theo loại Group (VD: PROJECT, DEPARTMENT...)")
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(groupService.findAll(type));
    }

    @Operation(summary = "Xem chi tiết một Group (Dự án / Phòng ban) theo ID")
    @GetMapping("/{id}")
    public ApiResponse<GroupResponse> getById(@Parameter(description = "UUID của Group") @PathVariable UUID id) {
        return ApiResponse.ok(groupService.getById(id));
    }

    @Operation(
            summary = "Lấy danh sách thành viên thuộc một Dự án / Group (để người tạo cuộc họp chọn người liên quan)",
            description = "Khi người tạo cuộc họp chọn họp theo Dự án (Group), gọi API này để hiển thị những thành viên đang tham gia dự án đó và tích chọn những người cần họp."
    )
    @GetMapping("/{id}/members")
    public ApiResponse<List<GroupResponse.GroupMemberDto>> getMembers(
            @Parameter(description = "UUID của Group") @PathVariable UUID id) {
        return ApiResponse.ok(groupService.getMembers(id));
    }

    @Operation(
            summary = "[Admin] Tạo Group (Dự án / Phòng ban) mới",
            description = "Yêu cầu quyền `GROUP_MANAGE`. Có thể truyền kèm danh sách `memberUserIds` (những người từ Team Dev, Team BA... tham gia vào Dự án A)."
    )
    @PostMapping
    @PreAuthorize("hasAuthority('GROUP_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> create(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo Group (Dự án / Phòng ban) thành công", groupService.createGroup(request)));
    }

    @Operation(summary = "[Admin] Thêm danh sách thành viên vào Dự án / Group", description = "Yêu cầu quyền `GROUP_MANAGE`.")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('GROUP_MANAGE')")
    public ApiResponse<GroupResponse> addMembers(
            @Parameter(description = "UUID của Group") @PathVariable UUID id,
            @Valid @RequestBody AddGroupMembersRequest request) {
        return ApiResponse.ok("Thêm thành viên vào Group thành công", groupService.addMembers(id, request));
    }

    @Operation(summary = "[Admin] Xóa thành viên khỏi Dự án / Group", description = "Yêu cầu quyền `GROUP_MANAGE`.")
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('GROUP_MANAGE')")
    public ApiResponse<Void> removeMember(
            @Parameter(description = "UUID của Group") @PathVariable UUID id,
            @Parameter(description = "UUID của người dùng") @PathVariable UUID userId) {
        groupService.removeMember(id, userId);
        return ApiResponse.ok("Đã xóa thành viên khỏi Group");
    }
}
