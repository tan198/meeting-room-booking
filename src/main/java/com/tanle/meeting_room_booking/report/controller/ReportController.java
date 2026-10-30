package com.tanle.meeting_room_booking.report.controller;

import com.tanle.meeting_room_booking.booking.entity.MeetingType;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.report.dto.BookingReportResponse;
import com.tanle.meeting_room_booking.report.dto.UsageReportResponse;
import com.tanle.meeting_room_booking.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "11. Reports & Analytics", description = "Thống kê đặt phòng, sử dụng thực tế có xác nhận vs kết thúc theo suy định, tỷ lệ NO_SHOW và bảo vệ quyền riêng tư CONFIDENTIAL (Spec mục 13, 16, 26 & 27.5)")
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "[Admin] Báo cáo thống kê đặt phòng theo khoảng thời gian và nhóm (GET /api/v1/reports/bookings - Spec 13 & 16)",
            description = """
                    Yêu cầu quyền `BOOKING_MANAGE`:
                    - Khoảng truy vấn tối đa 366 ngày (`fromTime` / `from` tới `toTime` / `to`).
                    - Tổng hợp theo trạng thái, Phòng, Loại cuộc họp (`MeetingType`), Team, Dự án và Phòng ban chủ trì.
                    - Bảo vệ riêng tư (`AC-76`): Gộp các cuộc họp `CONFIDENTIAL` thành nhóm riêng để không tiết lộ loại cuộc họp HR nhạy cảm hay team/dự án bị che.
                    """
    )
    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<BookingReportResponse> getBookingReport(
            @Parameter(description = "Từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Alias của fromTime (`from`)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Alias của toTime (`to`)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo UUID phòng ban chủ trì") @RequestParam(required = false) UUID departmentId,
            @Parameter(description = "Lọc theo UUID Team tổ chức") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "Lọc theo UUID Dự án") @RequestParam(required = false) UUID projectId,
            @Parameter(description = "Lọc theo loại cuộc họp") @RequestParam(required = false) MeetingType meetingType,
            @Parameter(description = "Lọc theo mức riêng tư (`STANDARD`, `CONFIDENTIAL`)") @RequestParam(required = false) Visibility visibility
    ) {
        OffsetDateTime resolvedFrom = fromTime != null ? fromTime : from;
        OffsetDateTime resolvedTo = toTime != null ? toTime : to;
        return ApiResponse.ok(reportService.getBookingReport(
                resolvedFrom, resolvedTo, roomId, departmentId, teamId, projectId, meetingType, visibility
        ));
    }

    @Operation(
            summary = "[Admin] Báo cáo sử dụng phòng thực tế, kết thúc theo suy định và tỷ lệ NO_SHOW (GET /api/v1/reports/usage - Spec 26.1 & AC-64..AC-66)",
            description = """
                    Yêu cầu quyền `BOOKING_MANAGE`:
                    - Tách bạch: giờ theo lịch bố trí (`scheduledApprovedMinutes`), giờ giữ phòng hiệu lực (`effectiveAllocatedMinutes`), giờ sử dụng có xác nhận kết thúc (`confirmedUsageMinutes`), giờ kết thúc theo suy định (`inferredUsageMinutes`) và giờ giải phóng sớm (`earlyReleasedMinutes`).
                    - Tính tỷ lệ `noShowRate` dựa trên tập mẫu các lịch đã duyệt có `checkInDeadline` trong kỳ.
                    """
    )
    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<UsageReportResponse> getUsageReport(
            @Parameter(description = "Từ thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @Parameter(description = "Alias của fromTime (`from`)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Đến thời điểm (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            @Parameter(description = "Alias của toTime (`to`)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo UUID phòng ban chủ trì") @RequestParam(required = false) UUID departmentId,
            @Parameter(description = "Lọc theo UUID Team tổ chức") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "Lọc theo UUID Dự án") @RequestParam(required = false) UUID projectId
    ) {
        OffsetDateTime resolvedFrom = fromTime != null ? fromTime : from;
        OffsetDateTime resolvedTo = toTime != null ? toTime : to;
        return ApiResponse.ok(reportService.getUsageReport(
                resolvedFrom, resolvedTo, roomId, departmentId, teamId, projectId
        ));
    }
}

