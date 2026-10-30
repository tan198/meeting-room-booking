package com.tanle.meeting_room_booking.report.service;

import com.tanle.meeting_room_booking.booking.entity.*;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.group.repository.WorkGroupRepository;
import com.tanle.meeting_room_booking.report.dto.BookingReportResponse;
import com.tanle.meeting_room_booking.report.dto.UsageReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final long MAX_REPORT_DAYS = 366;

    private final BookingRepository bookingRepository;
    private final WorkGroupRepository workGroupRepository;

    /**
     * Báo cáo tổng hợp đặt phòng (GET /api/v1/reports/bookings - Spec mục 13, 16, 26 & 27.5):
     * - Giới hạn khoảng truy vấn tối đa 366 ngày (Spec 23.1).
     * - Tính phần giao thời lượng với khoảng báo cáo [fromTime, toTime) (AC-43).
     * - Với các cuộc họp CONFIDENTIAL, gộp nhóm phân loại nhạy cảm (meetingType, team, project, department)
     *   thành nhóm "CONFIDENTIAL" để không làm lộ dữ liệu HR (Spec 27.5 & AC-76).
     */
    public BookingReportResponse getBookingReport(
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            UUID roomId,
            UUID departmentId,
            UUID teamId,
            UUID projectId,
            MeetingType meetingType,
            Visibility visibility
    ) {
        validateReportWindow(fromTime, toTime);
        OffsetDateTime now = OffsetDateTime.now();

        List<Booking> bookings = bookingRepository.findForReportInRange(
                fromTime, toTime, roomId, departmentId, teamId, projectId
        );

        // Áp bộ lọc bổ sung nhưng KHÔNG cho phép lọc theo meetingType/team/project nhạy cảm để suy ra lịch CONFIDENTIAL (Spec 27.5 & AC-73)
        bookings = bookings.stream()
                .filter(b -> visibility == null || b.getVisibility() == visibility)
                .filter(b -> {
                    if (meetingType == null) {
                        return true;
                    }
                    if (b.getVisibility() == Visibility.CONFIDENTIAL) {
                        return false;
                    }
                    return b.getMeetingType() == meetingType;
                })
                .toList();

        Map<UUID, String> deptNames = workGroupRepository.findAll().stream()
                .collect(Collectors.toMap(WorkGroup::getId, WorkGroup::getName, (a, b) -> a));

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        for (BookingStatus st : BookingStatus.values()) {
            statusCounts.put(st.name(), 0);
        }

        int noShowCount = 0;
        long totalScheduledApprovedMinutes = 0;
        long totalEffectiveAllocatedMinutes = 0;
        long totalConfirmedUsageMinutes = 0;
        long totalInferredUsageMinutes = 0;
        long totalEarlyReleasedMinutes = 0;

        Map<String, MutableGroupMetric> byRoom = new LinkedHashMap<>();
        Map<String, MutableGroupMetric> byMeetingType = new LinkedHashMap<>();
        Map<String, MutableGroupMetric> byTeam = new LinkedHashMap<>();
        Map<String, MutableGroupMetric> byProject = new LinkedHashMap<>();
        Map<String, MutableGroupMetric> byDepartment = new LinkedHashMap<>();

        for (Booking b : bookings) {
            statusCounts.merge(b.getStatus().name(), 1, Integer::sum);
            boolean isNoShow = b.getUsageStatus() == UsageStatus.NO_SHOW
                    || "NO_SHOW".equalsIgnoreCase(b.getCancellationReasonCode())
                    || b.getStatus() == BookingStatus.NO_SHOW;
            if (isNoShow) {
                noShowCount++;
            }

            BookingDurations d = computeClippedDurations(b, fromTime, toTime);
            totalScheduledApprovedMinutes += d.scheduledApprovedMinutes();
            totalEffectiveAllocatedMinutes += d.effectiveAllocatedMinutes();
            totalConfirmedUsageMinutes += d.confirmedUsageMinutes();
            totalInferredUsageMinutes += d.inferredUsageMinutes();
            totalEarlyReleasedMinutes += d.earlyReleasedMinutes();

            boolean isConf = b.getVisibility() == Visibility.CONFIDENTIAL;

            // 1. Nhóm theo Phòng (dữ liệu vận hành vẫn tính bình thường theo Spec 27.5)
            String roomKey = b.getRoom() != null ? b.getRoom().getId().toString() : "UNASSIGNED";
            String roomName = b.getRoom() != null ? b.getRoom().getName() : "Chưa phân phòng";
            byRoom.computeIfAbsent(roomKey, k -> new MutableGroupMetric(roomKey, roomName)).add(b, isNoShow, d);

            // 2. Nhóm theo Loại cuộc họp (gộp CONFIDENTIAL thành 1 nhóm riêng theo Spec 27.5 & AC-76)
            String mtKey = isConf ? "CONFIDENTIAL" : b.getMeetingType().name();
            String mtName = isConf ? "Cuộc họp riêng tư (CONFIDENTIAL)" : b.getMeetingType().getDescription();
            byMeetingType.computeIfAbsent(mtKey, k -> new MutableGroupMetric(mtKey, mtName)).add(b, isNoShow, d);

            // 3. Nhóm theo Team tổ chức
            String tKey = isConf ? "CONFIDENTIAL" : (b.getTeam() != null ? b.getTeam().getCode() : "NONE");
            String tName = isConf ? "Cuộc họp riêng tư (CONFIDENTIAL)" : (b.getTeam() != null ? b.getTeam().getName() : "Không gắn Team");
            byTeam.computeIfAbsent(tKey, k -> new MutableGroupMetric(tKey, tName)).add(b, isNoShow, d);

            // 4. Nhóm theo Dự án
            String pKey = isConf ? "CONFIDENTIAL" : (b.getGroup() != null ? b.getGroup().getCode() : "NONE");
            String pName = isConf ? "Cuộc họp riêng tư (CONFIDENTIAL)" : (b.getGroup() != null ? b.getGroup().getName() : "Không gắn Dự án");
            byProject.computeIfAbsent(pKey, k -> new MutableGroupMetric(pKey, pName)).add(b, isNoShow, d);

            // 5. Nhóm theo Đơn vị chủ trì (organizerDepartmentId)
            String dKey = b.getOrganizerDepartmentId() != null ? b.getOrganizerDepartmentId().toString() : "UNSPECIFIED";
            String dName = b.getOrganizerDepartmentId() != null
                    ? deptNames.getOrDefault(b.getOrganizerDepartmentId(), dKey)
                    : "Chưa gán Phòng ban";
            byDepartment.computeIfAbsent(dKey, k -> new MutableGroupMetric(dKey, dName)).add(b, isNoShow, d);
        }

        return new BookingReportResponse(
                fromTime,
                toTime,
                now,
                bookings.size(),
                statusCounts,
                noShowCount,
                totalScheduledApprovedMinutes,
                totalEffectiveAllocatedMinutes,
                totalConfirmedUsageMinutes,
                totalInferredUsageMinutes,
                totalEarlyReleasedMinutes,
                byRoom.values().stream().map(MutableGroupMetric::toRecord).toList(),
                byMeetingType.values().stream().map(MutableGroupMetric::toRecord).toList(),
                byTeam.values().stream().map(MutableGroupMetric::toRecord).toList(),
                byProject.values().stream().map(MutableGroupMetric::toRecord).toList(),
                byDepartment.values().stream().map(MutableGroupMetric::toRecord).toList()
        );
    }

    /**
     * Báo cáo chi tiết sử dụng phòng, tỷ lệ NO_SHOW, thời gian xác nhận thực tế và thời gian kết thúc theo suy định
     * (GET /api/v1/reports/usage - Spec mục 26.1 & AC-64..AC-66).
     */
    public UsageReportResponse getUsageReport(
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            UUID roomId,
            UUID departmentId,
            UUID teamId,
            UUID projectId
    ) {
        validateReportWindow(fromTime, toTime);
        OffsetDateTime now = OffsetDateTime.now();

        List<Booking> bookings = bookingRepository.findForReportInRange(
                fromTime, toTime, roomId, departmentId, teamId, projectId
        );

        int currentlyInUseCount = 0;
        int noShowCount = 0;
        int noShowSampleSize = 0;

        long totalScheduledApprovedMinutes = 0;
        long totalEffectiveAllocatedMinutes = 0;
        long totalConfirmedUsageMinutes = 0;
        long totalInferredUsageMinutes = 0;
        long totalEarlyReleasedMinutes = 0;

        Map<String, Long> earlyReleaseByReason = new LinkedHashMap<>();
        earlyReleaseByReason.put("EARLY_CHECKOUT", 0L);
        earlyReleaseByReason.put("NO_SHOW", 0L);
        earlyReleaseByReason.put("CANCELLED", 0L);

        Map<String, Integer> usageStatusCounts = new LinkedHashMap<>();
        for (UsageStatus us : UsageStatus.values()) {
            usageStatusCounts.put(us.name(), 0);
        }

        Map<UUID, MutableRoomUsage> roomMap = new LinkedHashMap<>();

        for (Booking b : bookings) {
            usageStatusCounts.merge(b.getUsageStatus().name(), 1, Integer::sum);

            if ((b.getUsageStatus() == UsageStatus.IN_USE || b.getStatus() == BookingStatus.IN_PROGRESS)
                    && now.isBefore(b.getEndTime())) {
                currentlyInUseCount++;
            }

            boolean isNoShow = b.getUsageStatus() == UsageStatus.NO_SHOW
                    || "NO_SHOW".equalsIgnoreCase(b.getCancellationReasonCode())
                    || b.getStatus() == BookingStatus.NO_SHOW;
            if (isNoShow) {
                noShowCount++;
            }

            // Tập mẫu tính tỷ lệ NO_SHOW theo Spec 26.1:
            // Gồm các booking từng được duyệt có checkInDeadline trong khoảng báo cáo và <= now, không bị hủy thủ công trước check-in.
            boolean wasEverApproved = b.getApprovedAt() != null
                    || b.getStatus() == BookingStatus.APPROVED
                    || b.getStatus() == BookingStatus.CONFIRMED
                    || b.getStatus() == BookingStatus.IN_PROGRESS
                    || b.getStatus() == BookingStatus.COMPLETED
                    || isNoShow
                    || b.getUsageStatus() == UsageStatus.INTERRUPTED;
            boolean manuallyCancelledBeforeCheckIn = b.getStatus() == BookingStatus.CANCELLED
                    && !isNoShow
                    && b.getUsageStatus() != UsageStatus.INTERRUPTED;

            OffsetDateTime deadline = b.getCheckInDeadline() != null
                    ? b.getCheckInDeadline()
                    : b.getStartTime().plusMinutes(10);
            if (wasEverApproved
                    && !manuallyCancelledBeforeCheckIn
                    && !deadline.isBefore(fromTime)
                    && deadline.isBefore(toTime)
                    && !deadline.isAfter(now)) {
                noShowSampleSize++;
            }

            BookingDurations d = computeClippedDurations(b, fromTime, toTime);
            totalScheduledApprovedMinutes += d.scheduledApprovedMinutes();
            totalEffectiveAllocatedMinutes += d.effectiveAllocatedMinutes();
            totalConfirmedUsageMinutes += d.confirmedUsageMinutes();
            totalInferredUsageMinutes += d.inferredUsageMinutes();
            totalEarlyReleasedMinutes += d.earlyReleasedMinutes();

            if (d.earlyReleasedMinutes() > 0) {
                if (isNoShow) {
                    earlyReleaseByReason.merge("NO_SHOW", d.earlyReleasedMinutes(), Long::sum);
                } else if (b.getStatus() == BookingStatus.CANCELLED || b.getUsageStatus() == UsageStatus.INTERRUPTED) {
                    earlyReleaseByReason.merge("CANCELLED", d.earlyReleasedMinutes(), Long::sum);
                } else {
                    earlyReleaseByReason.merge("EARLY_CHECKOUT", d.earlyReleasedMinutes(), Long::sum);
                }
            }

            if (b.getRoom() != null) {
                UUID rId = b.getRoom().getId();
                roomMap.computeIfAbsent(rId, k -> new MutableRoomUsage(rId, b.getRoom().getCode(), b.getRoom().getName()))
                        .add(b, isNoShow, d);
            }
        }

        Double noShowRate = noShowSampleSize == 0
                ? null
                : Math.round(((double) noShowCount * 10000.0) / noShowSampleSize) / 100.0;

        return new UsageReportResponse(
                fromTime,
                toTime,
                now,
                bookings.size(),
                currentlyInUseCount,
                noShowCount,
                noShowSampleSize,
                noShowRate,
                totalScheduledApprovedMinutes,
                totalEffectiveAllocatedMinutes,
                totalConfirmedUsageMinutes,
                totalInferredUsageMinutes,
                totalEarlyReleasedMinutes,
                earlyReleaseByReason,
                usageStatusCounts,
                roomMap.values().stream().map(MutableRoomUsage::toRecord).toList()
        );
    }

    private void validateReportWindow(OffsetDateTime fromTime, OffsetDateTime toTime) {
        if (fromTime == null || toTime == null || !fromTime.isBefore(toTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "fromTime phải nhỏ hơn toTime");
        }
        if (Duration.between(fromTime, toTime).toDays() > MAX_REPORT_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian truy vấn báo cáo tối đa là 366 ngày (Spec 23.1)");
        }
    }

    private BookingDurations computeClippedDurations(Booking b, OffsetDateTime reportStart, OffsetDateTime reportEnd) {
        boolean isApprovedActiveOrCompleted = b.getStatus() == BookingStatus.APPROVED
                || b.getStatus() == BookingStatus.CONFIRMED
                || b.getStatus() == BookingStatus.IN_PROGRESS
                || b.getStatus() == BookingStatus.COMPLETED;

        boolean wasAllocated = isApprovedActiveOrCompleted
                || b.getApprovedAt() != null
                || b.getUsageStatus() == UsageStatus.NO_SHOW
                || b.getUsageStatus() == UsageStatus.INTERRUPTED;

        long scheduledApproved = isApprovedActiveOrCompleted
                ? overlapMinutes(b.getStartTime(), b.getEndTime(), reportStart, reportEnd)
                : 0L;

        long effectiveAllocated = 0L;
        long earlyReleased = 0L;
        if (wasAllocated && b.getRoom() != null) {
            OffsetDateTime occupiedUntil = b.getEffectiveOccupiedUntil();
            effectiveAllocated = overlapMinutes(b.getStartTime(), occupiedUntil, reportStart, reportEnd);
            if (b.getReleasedAt() != null && b.getReleasedAt().isBefore(b.getEndTime())) {
                OffsetDateTime releaseStart = b.getReleasedAt().isAfter(b.getStartTime()) ? b.getReleasedAt() : b.getStartTime();
                earlyReleased = overlapMinutes(releaseStart, b.getEndTime(), reportStart, reportEnd);
            }
        }

        long confirmedUsage = 0L;
        long inferredUsage = 0L;

        // Theo Spec 26.1 & AC-64..AC-66:
        // - NO_SHOW có thời gian sử dụng = 0 (thời gian chờ trước deadline chỉ là giữ phòng).
        // - Có check-in (actualStartTime != null):
        //   + Nếu kết thúc bởi USER_CHECKOUT, ADMIN_CHECKOUT, CANCELLED_DURING_USE (hoặc có actualEndTime) -> tính vào confirmedUsageMinutes.
        //   + Nếu kết thúc bởi SYSTEM_SCHEDULED_END (không check-out thủ công) -> tính riêng vào inferredUsageMinutes.
        if (b.getActualStartTime() != null && b.getUsageStatus() != UsageStatus.NO_SHOW) {
            if ("SYSTEM_SCHEDULED_END".equalsIgnoreCase(b.getEndSource())) {
                inferredUsage = overlapMinutes(b.getActualStartTime(), b.getEndTime(), reportStart, reportEnd);
            } else if (b.getActualEndTime() != null) {
                confirmedUsage = overlapMinutes(b.getActualStartTime(), b.getActualEndTime(), reportStart, reportEnd);
            }
        }

        return new BookingDurations(scheduledApproved, effectiveAllocated, confirmedUsage, inferredUsage, earlyReleased);
    }

    private long overlapMinutes(
            OffsetDateTime segStart,
            OffsetDateTime segEnd,
            OffsetDateTime winStart,
            OffsetDateTime winEnd
    ) {
        if (segStart == null || segEnd == null || !segStart.isBefore(segEnd)) {
            return 0L;
        }
        OffsetDateTime s = segStart.isAfter(winStart) ? segStart : winStart;
        OffsetDateTime e = segEnd.isBefore(winEnd) ? segEnd : winEnd;
        if (!s.isBefore(e)) {
            return 0L;
        }
        return Duration.between(s, e).toMinutes();
    }

    private record BookingDurations(
            long scheduledApprovedMinutes,
            long effectiveAllocatedMinutes,
            long confirmedUsageMinutes,
            long inferredUsageMinutes,
            long earlyReleasedMinutes
    ) {}

    private static class MutableGroupMetric {
        private final String key;
        private final String name;
        private int bookingCount;
        private int approvedCount;
        private int cancelledCount;
        private int noShowCount;
        private long scheduledApprovedMinutes;
        private long effectiveAllocatedMinutes;
        private long confirmedUsageMinutes;
        private long inferredUsageMinutes;

        MutableGroupMetric(String key, String name) {
            this.key = key;
            this.name = name;
        }

        void add(Booking b, boolean isNoShow, BookingDurations d) {
            this.bookingCount++;
            if (b.getStatus() == BookingStatus.APPROVED
                    || b.getStatus() == BookingStatus.CONFIRMED
                    || b.getStatus() == BookingStatus.IN_PROGRESS
                    || b.getStatus() == BookingStatus.COMPLETED) {
                this.approvedCount++;
            }
            if (b.getStatus() == BookingStatus.CANCELLED) {
                this.cancelledCount++;
            }
            if (isNoShow) {
                this.noShowCount++;
            }
            this.scheduledApprovedMinutes += d.scheduledApprovedMinutes();
            this.effectiveAllocatedMinutes += d.effectiveAllocatedMinutes();
            this.confirmedUsageMinutes += d.confirmedUsageMinutes();
            this.inferredUsageMinutes += d.inferredUsageMinutes();
        }

        BookingReportResponse.GroupMetricItem toRecord() {
            return new BookingReportResponse.GroupMetricItem(
                    key, name, bookingCount, approvedCount, cancelledCount, noShowCount,
                    scheduledApprovedMinutes, effectiveAllocatedMinutes, confirmedUsageMinutes, inferredUsageMinutes
            );
        }
    }

    private static class MutableRoomUsage {
        private final UUID roomId;
        private final String roomCode;
        private final String roomName;
        private int approvedBookingCount;
        private int noShowCount;
        private int interruptedCount;
        private long scheduledApprovedMinutes;
        private long effectiveAllocatedMinutes;
        private long confirmedUsageMinutes;
        private long inferredUsageMinutes;
        private long earlyReleasedMinutes;

        MutableRoomUsage(UUID roomId, String roomCode, String roomName) {
            this.roomId = roomId;
            this.roomCode = roomCode;
            this.roomName = roomName;
        }

        void add(Booking b, boolean isNoShow, BookingDurations d) {
            if (b.getStatus() == BookingStatus.APPROVED
                    || b.getStatus() == BookingStatus.CONFIRMED
                    || b.getStatus() == BookingStatus.IN_PROGRESS
                    || b.getStatus() == BookingStatus.COMPLETED) {
                this.approvedBookingCount++;
            }
            if (isNoShow) {
                this.noShowCount++;
            }
            if (b.getUsageStatus() == UsageStatus.INTERRUPTED) {
                this.interruptedCount++;
            }
            this.scheduledApprovedMinutes += d.scheduledApprovedMinutes();
            this.effectiveAllocatedMinutes += d.effectiveAllocatedMinutes();
            this.confirmedUsageMinutes += d.confirmedUsageMinutes();
            this.inferredUsageMinutes += d.inferredUsageMinutes();
            this.earlyReleasedMinutes += d.earlyReleasedMinutes();
        }

        UsageReportResponse.RoomUsageItem toRecord() {
            return new UsageReportResponse.RoomUsageItem(
                    roomId, roomCode, roomName, approvedBookingCount, noShowCount, interruptedCount,
                    scheduledApprovedMinutes, effectiveAllocatedMinutes, confirmedUsageMinutes,
                    inferredUsageMinutes, earlyReleasedMinutes
            );
        }
    }
}

