package com.tanle.meeting_room_booking.booking.controller;

import com.tanle.meeting_room_booking.booking.dto.*;
import com.tanle.meeting_room_booking.booking.entity.BookingAuditLog;
import com.tanle.meeting_room_booking.booking.entity.BookingStatus;
import com.tanle.meeting_room_booking.booking.entity.MeetingType;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "4. Bookings & Booking Changes", description = "Gửi yêu cầu đặt phòng (PENDING), Admin phê duyệt/từ chối/chuyển phòng (APPROVED/REJECTED), đề nghị thay đổi lịch đã duyệt (BookingChange), Check-in/Check-out và Hủy lịch theo chuẩn Spec")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Danh mục loại cuộc họp (Meeting Types) và mức riêng tư tối thiểu (Spec 27.4 & 27.6)",
            description = "Trả về toàn bộ các loại cuộc họp hợp lệ cùng mức riêng tư tối thiểu (`STANDARD` hoặc bắt buộc `CONFIDENTIAL`)."
    )
    @GetMapping("/meeting-types")
    public ApiResponse<List<Map<String, Object>>> getMeetingTypes() {
        List<Map<String, Object>> types = Arrays.stream(MeetingType.values())
                .map(mt -> Map.<String, Object>of(
                        "code", mt.name(),
                        "description", mt.getDescription(),
                        "mandatoryConfidential", mt.isMandatoryConfidential(),
                        "minVisibility", mt.isMandatoryConfidential() ? Visibility.CONFIDENTIAL.name() : Visibility.STANDARD.name()
                ))
                .toList();
        return ApiResponse.ok(types);
    }

    @Operation(
            summary = "Gửi yêu cầu đặt phòng mới (khởi tạo ở trạng thái PENDING chờ Admin duyệt - Spec 8.1 & 8.2)",
            description = """
                    Tạo yêu cầu đặt phòng ở trạng thái `PENDING` (chưa giữ phòng chính thức):
                    - Hỗ trợ phân loại mục đích họp `meetingType` (`GENERAL`, `SYNC`, `PLANNING`, `REVIEW`, `SALARY_REVIEW`, `PERFORMANCE_REVIEW`, `INTERVIEW`, `TRAINING`, `OTHER`) và mức riêng tư `visibility` (`STANDARD`, `CONFIDENTIAL`).
                    - Các loại nhạy cảm (`SALARY_REVIEW`, `PERFORMANCE_REVIEW`, `INTERVIEW`) bắt buộc tự nâng `visibility = CONFIDENTIAL`.
                    - Cho phép chọn `preferredRoomId` (hoặc `roomId`), gắn `organizingTeamId` (`teamId`), `projectId` (`groupId`), `relatedDepartmentIds` và danh sách người tham dự nội bộ `participantIds` (`attendeeIds`).
                    - Tự động trả kèm cảnh báo `PREFERRED_ROOM_BUSY` hoặc `NO_MATCHING_ROOM` nếu phòng mong muốn đang bận.
                    """
    )
    @PostMapping("/bookings")
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi yêu cầu đặt phòng thành công (đang chờ phê duyệt)", bookingService.createBooking(principal, request)));
    }

    @Operation(
            summary = "[Admin] Phê duyệt yêu cầu đặt phòng và phân bổ phòng chính thức (Spec 8.3)",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Admin không được tự duyệt yêu cầu của chính mình (`SELF_APPROVAL_FORBIDDEN`). Kiểm tra `expectedVersion`, gán phòng chính thức và chuyển sang `APPROVED` + `NOT_CHECKED_IN`."
    )
    @PostMapping("/bookings/{id}/approve")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingResponse> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody ApproveBookingRequest request) {
        return ApiResponse.ok("Phê duyệt và phân phòng thành công", bookingService.approveBooking(principal, id, request));
    }

    @Operation(
            summary = "[Admin] Từ chối yêu cầu đặt phòng đang PENDING (Spec 8.4)",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Admin không được tự từ chối yêu cầu của chính mình; lý do từ chối là bắt buộc."
    )
    @PostMapping("/bookings/{id}/reject")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingResponse> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody RejectBookingRequest request) {
        return ApiResponse.ok("Đã từ chối yêu cầu đặt phòng", bookingService.rejectBooking(principal, id, request));
    }

    @Operation(
            summary = "[Admin] Chuyển phòng họp do vận hành / sự cố (Spec 11.2 & 16)",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Giữ nguyên thời gian dự kiến, danh sách người tham dự và trạng thái `APPROVED`."
    )
    @PostMapping("/bookings/{id}/reassign-room")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingResponse> reassignRoom(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody ReassignRoomRequest request) {
        return ApiResponse.ok("Chuyển phòng họp thành công", bookingService.reassignRoom(principal, id, request));
    }

    @Operation(
            summary = "Tạo đề nghị thay đổi cho lịch đã được duyệt (BookingChange - Spec 9 & AC-14)",
            description = "Chủ lịch gửi đề nghị đổi giờ, số người, thiết bị, người tham dự hoặc phòng mong muốn cho một lịch đang `APPROVED`. Lịch gốc vẫn giữ nguyên phòng cũ cho tới khi Admin duyệt."
    )
    @PostMapping("/bookings/{id}/changes")
    public ResponseEntity<ApiResponse<BookingChangeResponse>> createBookingChange(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking đã duyệt") @PathVariable UUID id,
            @Valid @RequestBody CreateBookingChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi đề nghị thay đổi lịch đặt phòng thành công", bookingService.createBookingChange(principal, id, request)));
    }

    @Operation(
            summary = "Xem danh sách đề nghị thay đổi (BookingChange) của một lịch đặt phòng",
            description = "Chủ lịch hoặc Quản trị viên (`BOOKING_MANAGE`) có thể xem lịch sử các đề nghị thay đổi của booking."
    )
    @GetMapping("/bookings/{id}/changes")
    public ApiResponse<List<BookingChangeResponse>> getBookingChanges(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getBookingChanges(principal, id));
    }

    @Operation(
            summary = "[Admin] Phê duyệt đề nghị thay đổi lịch đặt phòng (Spec 9 & AC-15)",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Admin không được tự duyệt đề nghị của chính mình (`SELF_APPROVAL_FORBIDDEN`). Áp dụng thông tin mới và phân phòng chính thức cho booking gốc."
    )
    @PostMapping("/booking-changes/{id}/approve")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingChangeResponse> approveBookingChange(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của đề nghị thay đổi (BookingChange)") @PathVariable UUID id,
            @Valid @RequestBody ApproveBookingRequest request) {
        return ApiResponse.ok("Phê duyệt đề nghị thay đổi lịch thành công", bookingService.approveBookingChange(principal, id, request));
    }

    @Operation(
            summary = "[Admin] Từ chối đề nghị thay đổi lịch đặt phòng (Spec 9 & AC-16)",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Lịch gốc vẫn giữ nguyên trạng thái `APPROVED` và phòng cũ."
    )
    @PostMapping("/booking-changes/{id}/reject")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingChangeResponse> rejectBookingChange(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của đề nghị thay đổi (BookingChange)") @PathVariable UUID id,
            @Valid @RequestBody RejectBookingRequest request) {
        return ApiResponse.ok("Đã từ chối đề nghị thay đổi lịch", bookingService.rejectBookingChange(principal, id, request));
    }

    @Operation(
            summary = "Chủ lịch rút lại đề nghị thay đổi đang chờ duyệt (Spec 9 & AC-17)",
            description = "Chủ lịch rút lại đề nghị `PENDING` trước khi Admin xử lý; lịch gốc giữ nguyên không đổi."
    )
    @PostMapping("/booking-changes/{id}/withdraw")
    public ApiResponse<BookingChangeResponse> withdrawBookingChange(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của đề nghị thay đổi (BookingChange)") @PathVariable UUID id,
            @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.ok("Đã rút lại đề nghị thay đổi lịch", bookingService.withdrawBookingChange(principal, id, request));
    }

    @Operation(
            summary = "Lịch họp cá nhân của tôi (hỗ trợ cả /api/v1/bookings/me và /api/v1/me/calendar theo Spec 16)",
            description = "Truy vấn qua bảng `booking_attendees` để lấy toàn bộ lịch họp liên quan đến người dùng hiện tại."
    )
    @GetMapping({"/bookings/me", "/me/calendar"})
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "Lọc theo Team (UUID của Team)") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "Lọc theo Dự án / Phòng ban (UUID của Group)") @RequestParam(required = false) UUID groupId,
            @Parameter(description = "Trạng thái booking") @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Lọc từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Lọc đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookingService.getMySchedule(principal, teamId, groupId, status, fromTime, toTime, page, size));
    }

    @Operation(
            summary = "Xem lịch biểu của một phòng họp (hỗ trợ cả /bookings/room/{roomId}/schedule và /rooms/{roomId}/calendar theo Spec 16)",
            description = "Trả về toàn bộ lịch đặt đã duyệt của phòng `roomId` giao với khoảng `[fromTime, toTime)`. Tuân thủ chính sách che trường `STANDARD` / `CONFIDENTIAL`."
    )
    @GetMapping({"/bookings/room/{roomId}/schedule", "/rooms/{roomId}/calendar"})
    public ApiResponse<List<BookingResponse>> getRoomSchedule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của phòng họp") @PathVariable UUID roomId,
            @Parameter(description = "Thời điểm bắt đầu (ISO-8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Thời điểm kết thúc (ISO-8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime) {
        return ApiResponse.ok(bookingService.getRoomSchedule(principal, roomId, fromTime, toTime));
    }

    @Operation(
            summary = "[Manager] Tìm kiếm danh sách yêu cầu và lịch đặt phòng",
            description = "Yêu cầu quyền `BOOKING_MANAGE`. Sắp xếp theo thời điểm gửi để ưu tiên xử lý yêu cầu gửi trước (POL-08)."
    )
    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<PageResponse<BookingResponse>> searchAll(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo UUID người đặt") @RequestParam(required = false) UUID organizerId,
            @Parameter(description = "Lọc theo Team (UUID của Team)") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "Lọc theo Dự án / Phòng ban (UUID của Group)") @RequestParam(required = false) UUID groupId,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Lọc từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Lọc đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookingService.search(principal, roomId, organizerId, teamId, groupId, status, fromTime, toTime, page, size));
    }

    @Operation(summary = "Xem chi tiết lịch đặt phòng theo ID")
    @GetMapping("/bookings/{id}")
    public ApiResponse<BookingResponse> getById(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getById(principal, id));
    }

    @Operation(
            summary = "Cập nhật yêu cầu đặt phòng (hỗ trợ cả PUT và PATCH /api/v1/bookings/{id})",
            description = "Chủ yêu cầu được sửa yêu cầu `PENDING` trước giờ bắt đầu (kèm kiểm tra `expectedVersion`)."
    )
    @RequestMapping(value = "/bookings/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<BookingResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody UpdateBookingRequest request) {
        return ApiResponse.ok("Cập nhật yêu cầu đặt phòng thành công", bookingService.updateBooking(principal, id, request));
    }

    @Operation(summary = "Phản hồi lời mời họp (Tương thích ngược)")
    @PostMapping("/bookings/{id}/rsvp")
    public ApiResponse<BookingResponse> respondRsvp(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody RsvpRequest request) {
        return ApiResponse.ok("Đã cập nhật phản hồi tham dự", bookingService.respondRsvp(principal, id, request));
    }

    @Operation(
            summary = "Hủy yêu cầu / lịch đặt phòng (Spec 8.5)",
            description = "Chủ lịch được hủy `PENDING` hoặc `APPROVED` trước `startTime`; Admin được hủy trước `endTime`. Lý do hủy (`reason`) là bắt buộc."
    )
    @PostMapping("/bookings/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody CancelBookingRequest request) {
        return ApiResponse.ok("Hủy lịch đặt phòng thành công", bookingService.cancelBooking(principal, id, request));
    }

    @Operation(
            summary = "Check-in xác nhận bắt đầu sử dụng phòng họp (Spec 25.3)",
            description = "Hợp lệ trong cửa sổ `startAt <= now < min(startAt + 10 phút, endAt)`. Người tổ chức, người tham dự nội bộ hoặc Admin (kèm lý do) được phép check-in."
    )
    @PostMapping("/bookings/{id}/check-in")
    public ApiResponse<BookingResponse> checkIn(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody(required = false) UsageActionRequest request) {
        return ApiResponse.ok("Check-in thành công", bookingService.checkIn(principal, id, request));
    }

    @Operation(
            summary = "Check-out xác nhận kết thúc và trả phòng họp sớm (Spec 25.4)",
            description = "Chỉ người tổ chức, người đã check-in hoặc Admin (kèm lý do) được phép check-out. Giải phóng ngay phần thời gian còn lại (`releasedAt`) mà vẫn giữ nguyên `endTime` dự kiến."
    )
    @PostMapping("/bookings/{id}/check-out")
    public ApiResponse<BookingResponse> checkOut(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id,
            @Valid @RequestBody(required = false) UsageActionRequest request) {
        return ApiResponse.ok("Check-out thành công", bookingService.checkOut(principal, id, request));
    }

    @Operation(
            summary = "Xem lịch sử dấu vết thao tác (Audit Logs / History) của một booking (Spec 16 & 23)",
            description = "Hỗ trợ cả `/api/v1/bookings/{id}/audit-logs` và `/api/v1/bookings/{id}/history`. Chủ lịch hoặc Quản trị viên được xem."
    )
    @GetMapping({"/bookings/{id}/audit-logs", "/bookings/{id}/history"})
    public ApiResponse<List<BookingAuditLog>> getAuditLogs(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của booking") @PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getAuditLogs(principal, id));
    }
}
