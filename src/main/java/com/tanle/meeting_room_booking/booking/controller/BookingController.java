package com.tanle.meeting_room_booking.booking.controller;

import com.tanle.meeting_room_booking.booking.dto.BookingResponse;
import com.tanle.meeting_room_booking.booking.dto.CancelBookingRequest;
import com.tanle.meeting_room_booking.booking.dto.CreateBookingRequest;
import com.tanle.meeting_room_booking.booking.dto.UpdateBookingRequest;
import com.tanle.meeting_room_booking.booking.entity.BookingStatus;
import com.tanle.meeting_room_booking.booking.service.BookingService;
import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "4. Bookings", description = "Đặt phòng họp, quản lý lịch cá nhân, check-in/check-out và hủy lịch")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Đặt phòng họp mới",
            description = "Tạo lịch đặt phòng ở trạng thái `CONFIRMED`. Chặn trùng lịch bằng cả kiểm tra ứng dụng và PostgreSQL Exclusion Constraint (`excl_bookings_no_room_overlap`)."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đặt phòng họp thành công", bookingService.createBooking(principal.id(), request)));
    }

    @Operation(
            summary = "Danh sách lịch đặt phòng của tôi",
            description = "Lấy danh sách các cuộc họp do người dùng hiện tại tổ chức (có phân trang và lọc theo trạng thái/thời gian)."
    )
    @GetMapping("/me")
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "Trạng thái booking") @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Lọc từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Lọc đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 50)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookingService.search(null, principal.id(), status, fromTime, toTime, page, size));
    }

    @Operation(
            summary = "Xem lịch biểu của một phòng họp trong khoảng thời gian",
            description = "Trả về toàn bộ lịch đặt của phòng `roomId` giao với khoảng `[fromTime, toTime)` để hiển thị trên Calendar."
    )
    @GetMapping("/room/{roomId}/schedule")
    public ApiResponse<List<BookingResponse>> getRoomSchedule(
            @Parameter(description = "UUID của phòng họp") @PathVariable UUID roomId,
            @Parameter(description = "Thời điểm bắt đầu (ISO-8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Thời điểm kết thúc (ISO-8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime) {
        return ApiResponse.ok(bookingService.getRoomSchedule(roomId, fromTime, toTime));
    }

    @Operation(
            summary = "[Manager] Tìm kiếm toàn bộ lịch đặt phòng",
            description = "Yêu cầu quyền `BOOKING_MANAGE`."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<PageResponse<BookingResponse>> searchAll(
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo UUID người đặt") @RequestParam(required = false) UUID organizerId,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Lọc từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Lọc đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 50)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookingService.search(roomId, organizerId, status, fromTime, toTime, page, size));
    }

    @Operation(summary = "Xem chi tiết lịch đặt phòng theo ID")
    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> getById(@Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getById(id));
    }

    @Operation(
            summary = "Cập nhật thông tin lịch đặt phòng",
            description = "Chỉ người tổ chức (organizer) hoặc Admin mới được phép sửa khi lịch đang ở trạng thái `CONFIRMED`."
    )
    @PutMapping("/{id}")
    public ApiResponse<BookingResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody UpdateBookingRequest request) {
        return ApiResponse.ok("Cập nhật lịch đặt phòng thành công", bookingService.updateBooking(principal, id, request));
    }

    @Operation(
            summary = "Hủy lịch đặt phòng",
            description = "Chỉ người tổ chức hoặc Admin được phép hủy khi lịch đang ở trạng thái `CONFIRMED` hoặc `IN_PROGRESS`. Lưu lại audit `cancelledBy`, `cancelledAt` và `cancelReason`."
    )
    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.ok("Hủy lịch đặt phòng thành công", bookingService.cancelBooking(principal, id, request));
    }

    @Operation(
            summary = "Check-in nhận phòng họp",
            description = "Chuyển trạng thái từ `CONFIRMED` sang `IN_PROGRESS` và ghi nhận `actualStartTime`. Cho phép check-in sớm tối đa 15 phút trước `startTime`."
    )
    @PostMapping("/{id}/check-in")
    public ApiResponse<BookingResponse> checkIn(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok("Check-in thành công", bookingService.checkIn(principal, id));
    }

    @Operation(
            summary = "Check-out trả phòng họp",
            description = "Chuyển trạng thái từ `IN_PROGRESS` sang `COMPLETED`, ghi nhận `actualEndTime` và giải phóng khung giờ còn lại nếu kết thúc sớm."
    )
    @PostMapping("/{id}/check-out")
    public ApiResponse<BookingResponse> checkOut(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok("Check-out thành công", bookingService.checkOut(principal, id));
    }
}

