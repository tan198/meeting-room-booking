package com.tanle.meeting_room_booking.issue.service;

import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.issue.dto.CreateRoomIssueRequest;
import com.tanle.meeting_room_booking.issue.dto.RoomIssueHistoryResponse;
import com.tanle.meeting_room_booking.issue.dto.RoomIssueResponse;
import com.tanle.meeting_room_booking.issue.dto.TransitionRoomIssueRequest;
import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import com.tanle.meeting_room_booking.issue.entity.RoomIssue;
import com.tanle.meeting_room_booking.issue.entity.RoomIssueHistory;
import com.tanle.meeting_room_booking.issue.repository.RoomIssueHistoryRepository;
import com.tanle.meeting_room_booking.issue.repository.RoomIssueRepository;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.service.RoomService;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomIssueService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RoomIssueRepository roomIssueRepository;
    private final RoomIssueHistoryRepository historyRepository;
    private final RoomService roomService;
    private final BookingRepository bookingRepository;
    private final UserRepo userRepo;
    private final NotificationService notificationService;

    /**
     * Mọi người dùng đang ACTIVE báo sự cố phòng/thiết bị (Spec mục 10.1 & AC-18..AC-20):
     * - Cho phép báo sự cố cho cả phòng đang INACTIVE (miễn là phòng tồn tại).
     * - KHÔNG tự động khóa phòng, chặn phòng hay hủy lịch (POL-14).
     * - Gửi thông báo cho tất cả Quản trị viên đang ACTIVE, ngoại trừ chính người tạo (AC-20).
     */
    @Transactional
    public RoomIssueResponse createIssue(AuthUser actor, CreateRoomIssueRequest rq) {
        Room room = roomService.findOrThrow(rq.roomId());
        User reporter = userRepo.findById(actor.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (rq.relatedBookingId() != null && !bookingRepository.existsById(rq.relatedBookingId())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Lịch họp liên quan (relatedBookingId) không tồn tại");
        }

        RoomIssue issue = RoomIssue.builder()
                .room(room)
                .reporter(reporter)
                .relatedBookingId(rq.relatedBookingId())
                .category(rq.category())
                .title(rq.title().trim())
                .description(rq.description().trim())
                .proposedImpact(rq.resolvedProposedImpact())
                .status(IssueStatus.OPEN)
                .build();

        RoomIssue saved = roomIssueRepository.saveAndFlush(issue);

        historyRepository.save(RoomIssueHistory.builder()
                .issueId(saved.getId())
                .actorId(actor.id())
                .fromStatus(null)
                .toStatus(IssueStatus.OPEN)
                .reason("Khởi tạo báo cáo sự cố")
                .build());

        Set<UUID> adminIds = userRepo.findActiveAdminIds().stream()
                .filter(id -> !id.equals(actor.id()))
                .collect(Collectors.toSet());
        if (!adminIds.isEmpty()) {
            notificationService.sendBulk(
                    adminIds,
                    saved.getRelatedBookingId(),
                    "ROOM_ISSUE_REPORTED",
                    "Báo cáo sự cố phòng họp mới: " + room.getCode(),
                    String.format("%s vừa báo sự cố [%s - %s] tại phòng %s (%s): %s",
                            reporter.getFullName(), saved.getCategory(), saved.getProposedImpact(),
                            room.getName(), room.getCode(), saved.getTitle()),
                    Map.of(
                            "issueId", saved.getId().toString(),
                            "roomId", room.getId().toString(),
                            "category", saved.getCategory().name(),
                            "proposedImpact", saved.getProposedImpact().name()
                    ),
                    "ISSUE_REPORTED:" + saved.getId()
            );
        }

        return RoomIssueResponse.from(saved);
    }

    /**
     * Quản trị viên chuyển trạng thái xử lý báo cáo sự cố (Spec mục 10.2 & AC-19, AC-25):
     * - OPEN -> IN_PROGRESS
     * - IN_PROGRESS -> RESOLVED (bắt buộc resolution)
     * - RESOLVED -> IN_PROGRESS (bắt buộc reason)
     * - RESOLVED -> CLOSED
     * - OPEN -> CLOSED (bắt buộc reason)
     * - Giải quyết hoặc đóng RoomIssue KHÔNG tự động hủy RoomBlock liên quan (AC-25).
     */
    @Transactional
    public RoomIssueResponse transitionIssue(AuthUser admin, UUID issueId, TransitionRoomIssueRequest rq) {
        RoomIssue issue = findOrThrow(issueId);

        if (rq.expectedVersion() != null && issue.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_MISMATCH);
        }

        IssueStatus current = issue.getStatus();
        IssueStatus target = rq.targetStatus();

        validateTransition(current, target, rq.resolution(), rq.reason());

        issue.setStatus(target);
        if (rq.actualImpact() != null) {
            issue.setActualImpact(rq.actualImpact());
        }
        if (rq.resolution() != null && !rq.resolution().isBlank()) {
            issue.setResolution(rq.resolution().trim());
        }
        issue.setProcessedBy(admin.id());
        issue.setProcessedAt(OffsetDateTime.now());

        RoomIssue saved = roomIssueRepository.saveAndFlush(issue);

        historyRepository.save(RoomIssueHistory.builder()
                .issueId(saved.getId())
                .actorId(admin.id())
                .fromStatus(current)
                .toStatus(target)
                .reason(rq.reason() != null ? rq.reason().trim() : null)
                .resolution(rq.resolution() != null ? rq.resolution().trim() : null)
                .build());

        if (!saved.getReporter().getId().equals(admin.id())) {
            notificationService.send(
                    saved.getReporter().getId(),
                    saved.getRelatedBookingId(),
                    "ROOM_ISSUE_UPDATED",
                    "Cập nhật xử lý sự cố phòng " + saved.getRoom().getCode(),
                    String.format("Báo cáo sự cố '%s' tại phòng %s đã chuyển từ %s sang %s.",
                            saved.getTitle(), saved.getRoom().getName(), current, target),
                    Map.of(
                            "issueId", saved.getId().toString(),
                            "fromStatus", current.name(),
                            "toStatus", target.name()
                    ),
                    "ISSUE_TRANSITION:" + saved.getId() + ":" + saved.getVersion()
            );
        }

        return RoomIssueResponse.from(saved);
    }

    public RoomIssueResponse getById(AuthUser viewer, UUID issueId) {
        RoomIssue issue = findOrThrow(issueId);
        assertCanViewIssue(viewer, issue);
        return RoomIssueResponse.from(issue);
    }

    public PageResponse<RoomIssueResponse> search(
            AuthUser viewer,
            UUID roomId,
            IssueStatus status,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        UUID effectiveReporterId = isAdminOrManager(viewer) ? null : viewer.id();

        Page<RoomIssue> result = roomIssueRepository.searchIssues(
                effectiveReporterId,
                roomId,
                status,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending())
        );
        return PageResponse.from(result.map(RoomIssueResponse::from));
    }

    public List<RoomIssueHistoryResponse> getHistory(AuthUser viewer, UUID issueId) {
        RoomIssue issue = findOrThrow(issueId);
        assertCanViewIssue(viewer, issue);
        return historyRepository.findByIssueIdOrderByCreatedAtDesc(issueId).stream()
                .map(RoomIssueHistoryResponse::from)
                .toList();
    }

    public RoomIssue findOrThrow(UUID issueId) {
        return roomIssueRepository.findWithDetailsById(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
    }

    private void assertCanViewIssue(AuthUser viewer, RoomIssue issue) {
        if (!isAdminOrManager(viewer) && !issue.getReporter().getId().equals(viewer.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Người báo sự cố chỉ xem được các báo cáo do chính mình tạo");
        }
    }

    private boolean isAdminOrManager(AuthUser viewer) {
        return "ADMIN".equalsIgnoreCase(viewer.role())
                || viewer.hasAuthority("ROOM_UPDATE")
                || viewer.hasAuthority("BOOKING_MANAGE");
    }

    private void validateTransition(IssueStatus current, IssueStatus target, String resolution, String reason) {
        if (current == target || current == IssueStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_ISSUE_TRANSITION);
        }

        switch (current) {
            case OPEN -> {
                if (target == IssueStatus.IN_PROGRESS) {
                    return;
                }
                if (target == IssueStatus.CLOSED) {
                    if (reason == null || reason.isBlank()) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đóng trực tiếp báo cáo sự cố (OPEN -> CLOSED) bắt buộc phải có lý do (reason)");
                    }
                    return;
                }
                throw new BusinessException(ErrorCode.INVALID_ISSUE_TRANSITION);
            }
            case IN_PROGRESS -> {
                if (target == IssueStatus.RESOLVED) {
                    if (resolution == null || resolution.isBlank()) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chuyển sang RESOLVED bắt buộc phải ghi cách khắc phục (resolution)");
                    }
                    return;
                }
                throw new BusinessException(ErrorCode.INVALID_ISSUE_TRANSITION);
            }
            case RESOLVED -> {
                if (target == IssueStatus.CLOSED) {
                    return;
                }
                if (target == IssueStatus.IN_PROGRESS) {
                    if (reason == null || reason.isBlank()) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mở lại sự cố từ RESOLVED -> IN_PROGRESS bắt buộc phải có lý do (reason)");
                    }
                    return;
                }
                throw new BusinessException(ErrorCode.INVALID_ISSUE_TRANSITION);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_ISSUE_TRANSITION);
        }
    }
}

