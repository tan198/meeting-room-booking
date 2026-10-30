package com.tanle.meeting_room_booking.announcement.controller;

import com.tanle.meeting_room_booking.announcement.dto.AnnouncementPreviewResponse;
import com.tanle.meeting_room_booking.announcement.dto.AnnouncementResponse;
import com.tanle.meeting_room_booking.announcement.dto.CreateAnnouncementRequest;
import com.tanle.meeting_room_booking.announcement.dto.PreviewAnnouncementRequest;
import com.tanle.meeting_room_booking.announcement.service.AnnouncementService;
import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
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
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Tag(name = "10. Announcements", description = "Phát hành thông báo chung của Quản trị viên theo phạm vi (ALL, DEPARTMENTS, TEAMS, PROJECTS, BOOKINGS) kèm xem trước số lượng người nhận (Spec mục 12.3 & 22 UC-04)")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(
            summary = "[Admin] Xem trước số tài khoản ACTIVE duy nhất nhận thông báo theo phạm vi (Spec 22 UC-04 & 23.5)",
            description = "Yêu cầu quyền `BOOKING_MANAGE` hoặc `ROOM_UPDATE`. Loại trùng người nhận nếu một nhân viên thuộc nhiều nhóm."
    )
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE') or hasAuthority('ROOM_UPDATE')")
    public ApiResponse<AnnouncementPreviewResponse> preview(@Valid @RequestBody PreviewAnnouncementRequest request) {
        return ApiResponse.ok(announcementService.previewAudience(request));
    }

    @Operation(
            summary = "[Admin] Phát hành thông báo chung theo phạm vi (Spec 12.3 & AC-28, AC-31, AC-41)",
            description = "Yêu cầu quyền `BOOKING_MANAGE` hoặc `ROOM_UPDATE`. Chốt danh sách tài khoản ACTIVE tại thời điểm phát hành; nhân viên vào nhóm sau đó không nhận ngược thông báo cũ (`AC-41`)."
    )
    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_MANAGE') or hasAuthority('ROOM_UPDATE')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> publish(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Phát hành thông báo chung thành công", announcementService.publish(principal, request)));
    }

    @Operation(
            summary = "[Admin] Danh sách các thông báo chung đã phát hành (Spec 23.5)",
            description = "Yêu cầu quyền `BOOKING_MANAGE` hoặc `ROOM_UPDATE`."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_MANAGE') or hasAuthority('ROOM_UPDATE')")
    public ApiResponse<PageResponse<AnnouncementResponse>> listAll(
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(announcementService.listAll(page, size));
    }

    @Operation(
            summary = "[Admin] Xem chi tiết thông báo chung đã phát hành (Spec 23.5)",
            description = "Yêu cầu quyền `BOOKING_MANAGE` hoặc `ROOM_UPDATE`."
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE') or hasAuthority('ROOM_UPDATE')")
    public ApiResponse<AnnouncementResponse> getById(@Parameter(description = "UUID của thông báo chung") @PathVariable UUID id) {
        return ApiResponse.ok(announcementService.getById(id));
    }
}

