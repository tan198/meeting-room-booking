package com.tanle.meeting_room_booking.notification.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.notification.dto.NotificationResponse;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/v1/me/notifications"})
@RequiredArgsConstructor
@Tag(name = "8. Notifications", description = "Hộp thông báo cá nhân (hỗ trợ cả /api/v1/notifications và /api/v1/me/notifications theo Spec 16)")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Danh sách thông báo của tôi", description = "Hỗ trợ lọc chỉ thông báo chưa đọc (`unreadOnly=true`) và phân trang.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "Chỉ lấy thông báo chưa đọc") @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 50)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.getMyNotifications(principal.id(), unreadOnly, page, size));
    }

    @Operation(summary = "Đếm số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(Map.of("unreadCount", notificationService.countUnread(principal.id())));
    }

    @Operation(summary = "Đánh dấu đã đọc một thông báo")
    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của thông báo") @PathVariable UUID id) {
        return ApiResponse.ok(notificationService.markAsRead(principal.id(), id));
    }

    @Operation(summary = "Đánh dấu đã đọc toàn bộ thông báo của tôi")
    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal) {
        int updated = notificationService.markAllAsRead(principal.id());
        return ApiResponse.ok("Đã đánh dấu đọc tất cả thông báo", Map.of("updatedCount", updated));
    }
}

