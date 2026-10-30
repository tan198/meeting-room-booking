package com.tanle.meeting_room_booking.issue.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.issue.dto.CreateRoomIssueRequest;
import com.tanle.meeting_room_booking.issue.dto.RoomIssueHistoryResponse;
import com.tanle.meeting_room_booking.issue.dto.RoomIssueResponse;
import com.tanle.meeting_room_booking.issue.dto.TransitionRoomIssueRequest;
import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import com.tanle.meeting_room_booking.issue.service.RoomIssueService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/room-issues")
@RequiredArgsConstructor
@Tag(name = "8. Room Issues", description = "Báo cáo sự cố phòng họp / thiết bị và theo dõi vòng đời xử lý OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (Spec mục 10)")
public class RoomIssueController {

    private final RoomIssueService roomIssueService;

    @Operation(
            summary = "Tạo báo cáo sự cố phòng họp / thiết bị (Spec 10.1 & AC-18..AC-20)",
            description = "Mọi người dùng đang ACTIVE được báo sự cố cho phòng tồn tại (kể cả phòng đang INACTIVE). Không tự động khóa hay chặn phòng (POL-14). Gửi thông báo cho các Quản trị viên đang ACTIVE."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<RoomIssueResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CreateRoomIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi báo cáo sự cố thành công", roomIssueService.createIssue(principal, request)));
    }

    @Operation(
            summary = "Danh sách báo cáo sự cố phòng họp (Spec 10.1)",
            description = "Người dùng thường chỉ xem các báo cáo do chính mình tạo; Quản trị viên xem toàn bộ báo cáo."
    )
    @GetMapping
    public ApiResponse<PageResponse<RoomIssueResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo trạng thái (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`)") @RequestParam(required = false) IssueStatus status,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roomIssueService.search(principal, roomId, status, page, size));
    }

    @Operation(summary = "Xem chi tiết báo cáo sự cố phòng họp")
    @GetMapping("/{id}")
    public ApiResponse<RoomIssueResponse> getById(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của báo cáo sự cố") @PathVariable UUID id) {
        return ApiResponse.ok(roomIssueService.getById(principal, id));
    }

    @Operation(
            summary = "[Admin] Chuyển trạng thái xử lý báo cáo sự cố (Spec 10.2 & AC-19, AC-25)",
            description = """
                    Yêu cầu quyền `ROOM_UPDATE` hoặc `BOOKING_MANAGE`:
                    - `OPEN -> IN_PROGRESS`: bắt đầu xử lý
                    - `IN_PROGRESS -> RESOLVED`: bắt buộc ghi cách khắc phục (`resolution`)
                    - `RESOLVED -> IN_PROGRESS`: mở lại nếu tái phát, bắt buộc có lý do (`reason`)
                    - `RESOLVED -> CLOSED`: hoàn tất
                    - `OPEN -> CLOSED`: đóng nếu báo nhầm/trùng, bắt buộc có lý do (`reason`)
                    """
    )
    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<RoomIssueResponse> transition(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của báo cáo sự cố") @PathVariable UUID id,
            @Valid @RequestBody TransitionRoomIssueRequest request) {
        return ApiResponse.ok("Cập nhật trạng thái sự cố thành công", roomIssueService.transitionIssue(principal, id, request));
    }

    @Operation(summary = "Xem lịch sử chuyển trạng thái của báo cáo sự cố (Spec 16 & 23)")
    @GetMapping("/{id}/history")
    public ApiResponse<List<RoomIssueHistoryResponse>> getHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của báo cáo sự cố") @PathVariable UUID id) {
        return ApiResponse.ok(roomIssueService.getHistory(principal, id));
    }
}

