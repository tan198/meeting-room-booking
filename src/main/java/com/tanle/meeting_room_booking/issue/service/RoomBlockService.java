package com.tanle.meeting_room_booking.issue.service;

import com.tanle.meeting_room_booking.booking.dto.CancelBookingRequest;
import com.tanle.meeting_room_booking.booking.dto.ReassignRoomRequest;
import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.entity.BookingAttendee;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
import com.tanle.meeting_room_booking.booking.repository.BookingAttendeeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.booking.service.BookingService;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.issue.dto.*;
import com.tanle.meeting_room_booking.issue.entity.RoomBlock;
import com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus;
import com.tanle.meeting_room_booking.issue.entity.RoomIssue;
import com.tanle.meeting_room_booking.issue.repository.RoomBlockRepository;
import com.tanle.meeting_room_booking.issue.repository.RoomIssueRepository;
import com.tanle.meeting_room_booking.room.dto.RoomResponse;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.repository.RoomRepository;
import com.tanle.meeting_room_booking.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomBlockService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RoomBlockRepository roomBlockRepository;
    private final RoomIssueRepository roomIssueRepository;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttendeeRepository attendeeRepository;
    private final BookingService bookingService;

    /**
     * Bước 1: Xem trước tác động khi dự kiến chặn phòng (POST /api/v1/room-blocks/preview - Spec mục 11.2):
     * - Trả về danh sách các lịch đặt đang APPROVED còn chiếm chỗ trong khoảng [startTime, endTime) kèm version hiện tại.
     * - Gợi ý danh sách các phòng thay thế khả dụng (suggestedRooms) đáp ứng sức chứa cho từng booking.
     */
    public RoomBlockPreviewResponse previewBlock(AuthUser admin, PreviewRoomBlockRequest rq) {
        Room room = roomService.findOrThrow(rq.roomId());
        TimeWindow window = resolveAndValidateTimeWindow(
                rq.resolvedStartTime(),
                rq.resolvedEndTime(),
                rq.isImmediate()
        );

        List<Booking> affected = bookingRepository.findAffectedActiveBookingsForRoom(
                room.getId(),
                window.startTime(),
                window.endTime(),
                OffsetDateTime.now()
        );

        List<UUID> bookingIds = affected.stream().map(Booking::getId).toList();
        Map<UUID, List<BookingAttendee>> attendeesByBooking = bookingIds.isEmpty()
                ? Map.of()
                : attendeeRepository.findById_BookingIdIn(bookingIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getId().getBookingId()));

        List<RoomBlockPreviewResponse.AffectedBookingItem> items = new ArrayList<>();
        for (Booking b : affected) {
            List<BookingAttendee> attendees = attendeesByBooking.getOrDefault(b.getId(), List.of());
            boolean isOrganizerOrAttendee = b.getOrganizer().getId().equals(admin.id())
                    || attendees.stream().anyMatch(a -> a.getUser().getId().equals(admin.id()));

            // Theo Spec 27.3: Cuộc họp CONFIDENTIAL ẩn title ngay cả với Admin nếu Admin không phải organizer/attendee
            String displayTitle = (b.getVisibility() == Visibility.CONFIDENTIAL && !isOrganizerOrAttendee)
                    ? "Cuộc họp riêng tư"
                    : b.getTitle();

            List<RoomResponse> suggestedRooms = roomRepository.findAvailableRooms(
                            b.getStartTime(),
                            b.getEndTime(),
                            b.getAttendeeCount(),
                            null
                    ).stream()
                    .filter(r -> !r.getId().equals(room.getId()))
                    .map(RoomResponse::from)
                    .toList();

            items.add(new RoomBlockPreviewResponse.AffectedBookingItem(
                    b.getId(),
                    b.getVersion(),
                    displayTitle,
                    b.getMeetingType(),
                    b.getVisibility(),
                    b.getStatus(),
                    b.getUsageStatus(),
                    b.getStartTime(),
                    b.getEndTime(),
                    b.getAttendeeCount(),
                    List.of(),
                    b.getOrganizer().getId(),
                    b.getOrganizer().getFullName(),
                    suggestedRooms
            ));
        }

        return new RoomBlockPreviewResponse(
                room.getId(),
                room.getCode(),
                room.getName(),
                window.startTime(),
                window.endTime(),
                items.size(),
                items
        );
    }

    /**
     * Bước 2: Tạo khoảng chặn phòng nguyên tử kèm phương án xử lý toàn bộ booking bị ảnh hưởng (Spec 11.2 & AC-21..AC-23).
     */
    @Transactional
    public RoomBlockResponse createBlock(AuthUser admin, CreateRoomBlockRequest rq) {
        return createOrReplaceBlockInternal(admin, rq, null);
    }

    /**
     * Hủy khoảng chặn phòng (POST /api/v1/room-blocks/{id}/cancel - Spec 11.3 & AC-24):
     * - Mở lại khả năng phân phòng trong khoảng đó.
     * - KHÔNG tự khôi phục các booking đã hủy hoặc đã chuyển sang phòng khác (AC-24).
     */
    @Transactional
    public RoomBlockResponse cancelBlock(AuthUser admin, UUID blockId, CancelRoomBlockRequest rq) {
        RoomBlock block = findOrThrow(blockId);

        if (rq.expectedVersion() != null && block.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_MISMATCH);
        }
        if (block.getStatus() != RoomBlockStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Khoảng chặn phòng đã ở trạng thái CANCELLED");
        }

        block.setStatus(RoomBlockStatus.CANCELLED);
        block.setCancelledBy(admin.id());
        block.setCancelledAt(OffsetDateTime.now());
        block.setCancelReason(rq.reason().trim());

        return RoomBlockResponse.from(roomBlockRepository.saveAndFlush(block));
    }

    /**
     * Thay thế khoảng chặn phòng hiện có bằng khoảng chặn mới trong một giao dịch nguyên tử (POST /api/v1/room-blocks/{id}/replace - Spec 11.3).
     */
    @Transactional
    public RoomBlockResponse replaceBlock(AuthUser admin, UUID oldBlockId, CreateRoomBlockRequest rq) {
        RoomBlock oldBlock = findOrThrow(oldBlockId);

        if (rq.expectedVersion() != null && oldBlock.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_MISMATCH);
        }
        if (oldBlock.getStatus() != RoomBlockStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể thay thế khoảng chặn phòng đang ACTIVE");
        }

        oldBlock.setStatus(RoomBlockStatus.CANCELLED);
        oldBlock.setCancelledBy(admin.id());
        oldBlock.setCancelledAt(OffsetDateTime.now());
        oldBlock.setCancelReason("Thay thế bởi khoảng chặn phòng mới: " + rq.reason().trim());
        roomBlockRepository.saveAndFlush(oldBlock);

        RoomBlockResponse newBlockResp = createOrReplaceBlockInternal(admin, rq, oldBlock.getId());
        oldBlock.setReplacedById(newBlockResp.id());
        roomBlockRepository.saveAndFlush(oldBlock);

        return newBlockResp;
    }

    public RoomBlockResponse getById(UUID blockId) {
        return RoomBlockResponse.from(findOrThrow(blockId));
    }

    public PageResponse<RoomBlockResponse> search(UUID roomId, RoomBlockStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<RoomBlock> result = roomBlockRepository.searchBlocks(
                roomId,
                status,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("startTime").descending())
        );
        return PageResponse.from(result.map(RoomBlockResponse::from));
    }

    public RoomBlock findOrThrow(UUID blockId) {
        return roomBlockRepository.findWithDetailsById(blockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_BLOCK_NOT_FOUND));
    }

    private RoomBlockResponse createOrReplaceBlockInternal(
            AuthUser admin,
            CreateRoomBlockRequest rq,
            UUID excludedOldBlockId
    ) {
        Room room = roomService.findForUpdateOrThrow(rq.roomId());
        TimeWindow window = resolveAndValidateTimeWindow(
                rq.resolvedStartTime(),
                rq.resolvedEndTime(),
                rq.isImmediate()
        );

        RoomIssue issue = null;
        if (rq.issueId() != null) {
            issue = roomIssueRepository.findById(rq.issueId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        }

        // Kiểm tra trùng với các RoomBlock đang ACTIVE khác của cùng phòng
        if (roomBlockRepository.existsOverlappingActiveBlock(
                room.getId(), window.startTime(), window.endTime(), excludedOldBlockId)) {
            throw new BusinessException(
                    ErrorCode.ROOM_UNAVAILABLE,
                    "Phòng họp đã có khoảng chặn (RoomBlock) đang ACTIVE chồng lấn trong khung giờ này"
            );
        }

        // Truy vấn lại toàn bộ các booking đang chiếm chỗ trong khoảng chặn tại thời điểm thực thi giao dịch
        List<Booking> currentAffected = bookingRepository.findAffectedActiveBookingsForRoom(
                room.getId(),
                window.startTime(),
                window.endTime(),
                OffsetDateTime.now()
        );

        List<CreateRoomBlockRequest.BookingResolutionItem> resolutions = rq.resolutions() == null
                ? List.of()
                : rq.resolutions();

        Map<UUID, CreateRoomBlockRequest.BookingResolutionItem> resolutionMap = new LinkedHashMap<>();
        for (CreateRoomBlockRequest.BookingResolutionItem item : resolutions) {
            if (resolutionMap.put(item.bookingId(), item) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trùng lặp bookingId trong danh sách phương án xử lý: " + item.bookingId());
            }
        }

        // 1. Kiểm tra nếu có booking đang bị ảnh hưởng mà không có phương án xử lý -> IMPACT_PLAN_INCOMPLETE (AC-21)
        List<UUID> missingBookingIds = currentAffected.stream()
                .map(Booking::getId)
                .filter(id -> !resolutionMap.containsKey(id))
                .toList();
        if (!missingBookingIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.IMPACT_PLAN_INCOMPLETE,
                    "Phương án xử lý chưa bao phủ toàn bộ các lịch đặt đang bị ảnh hưởng trong khoảng chặn (thiếu " + missingBookingIds.size() + " booking)",
                    Map.of("missingBookingIds", missingBookingIds.stream().map(UUID::toString).toList())
            );
        }

        Map<UUID, Booking> affectedById = currentAffected.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));

        // 2. Kiểm tra nếu tập lịch bị ảnh hưởng hoặc version đã thay đổi từ lúc preview -> IMPACT_PLAN_STALE (AC-22)
        for (CreateRoomBlockRequest.BookingResolutionItem item : resolutions) {
            Booking target = affectedById.get(item.bookingId());
            if (target == null) {
                throw new BusinessException(
                        ErrorCode.IMPACT_PLAN_STALE,
                        "Lịch đặt " + item.bookingId() + " không còn nằm trong danh sách bị ảnh hưởng; vui lòng chạy lại bước preview",
                        Map.of("staleBookingId", item.bookingId().toString())
                );
            }
            if (item.expectedVersion() != null && target.getVersion() != item.expectedVersion()) {
                throw new BusinessException(
                        ErrorCode.IMPACT_PLAN_STALE,
                        "Phiên bản của lịch đặt " + item.bookingId() + " đã thay đổi từ lúc preview; vui lòng chạy lại bước preview",
                        Map.of(
                                "staleBookingId", item.bookingId().toString(),
                                "expectedVersion", item.expectedVersion(),
                                "actualVersion", target.getVersion()
                        )
                );
            }
        }

        // 3. Thực thi nguyên tử toàn bộ các phương án REASSIGN / CANCEL trong cùng giao dịch (AC-23)
        for (Booking booking : currentAffected) {
            CreateRoomBlockRequest.BookingResolutionItem plan = resolutionMap.get(booking.getId());
            String action = plan.action().trim().toUpperCase(Locale.ROOT);
            String actionReason = (plan.reason() != null && !plan.reason().isBlank())
                    ? plan.reason().trim()
                    : rq.reason().trim();

            switch (action) {
                case "REASSIGN" -> {
                    UUID newRoomId = plan.resolvedTargetRoomId();
                    if (newRoomId == null) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "Phương án REASSIGN cho booking " + booking.getId() + " bắt buộc phải chỉ định phòng đích (roomId)"
                        );
                    }
                    if (newRoomId.equals(room.getId())) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "Không thể chuyển phòng sang chính phòng đang bị chặn (" + room.getCode() + ")"
                        );
                    }
                    bookingService.reassignRoom(
                            admin,
                            booking.getId(),
                            new ReassignRoomRequest(booking.getVersion(), newRoomId, actionReason)
                    );
                }
                case "CANCEL" -> bookingService.cancelBooking(
                        admin,
                        booking.getId(),
                        new CancelBookingRequest(booking.getVersion(), actionReason)
                );
                default -> throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Hành động xử lý không hợp lệ cho booking " + booking.getId() + ": " + plan.action() + " (chỉ chấp nhận REASSIGN hoặc CANCEL)"
                );
            }
        }

        RoomBlock roomBlock = RoomBlock.builder()
                .room(room)
                .issue(issue)
                .startTime(window.startTime())
                .endTime(window.endTime())
                .reason(rq.reason().trim())
                .status(RoomBlockStatus.ACTIVE)
                .createdBy(admin.id())
                .build();

        return RoomBlockResponse.from(roomBlockRepository.saveAndFlush(roomBlock));
    }

    private TimeWindow resolveAndValidateTimeWindow(
            OffsetDateTime rawStart,
            OffsetDateTime rawEnd,
            boolean immediate
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        if (rawEnd == null) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời điểm kết thúc chặn (endTime / endAt) là bắt buộc");
        }

        OffsetDateTime start;
        if (immediate) {
            start = (rawStart == null || rawStart.isBefore(now)) ? now : rawStart;
            if (!start.isBefore(rawEnd)) {
                throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời gian bắt đầu chặn phải nhỏ hơn thời gian kết thúc");
            }
        } else {
            if (rawStart == null) {
                throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời điểm bắt đầu chặn (startTime / startAt) là bắt buộc khi immediate = false");
            }
            if (rawStart.isBefore(now.minusMinutes(1))) {
                throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng chặn theo kế hoạch không được bắt đầu trong quá khứ");
            }
            if (!rawStart.isBefore(rawEnd)) {
                throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời gian bắt đầu chặn phải nhỏ hơn thời gian kết thúc");
            }
            if (!is15MinuteAligned(rawStart) || !is15MinuteAligned(rawEnd)) {
                throw new BusinessException(
                        ErrorCode.INVALID_TIME_STEP,
                        "Khoảng chặn phòng theo kế hoạch (immediate = false) phải căn đúng mốc 15 phút (:00, :15, :30, :45)"
                );
            }
            start = rawStart;
        }

        return new TimeWindow(start, rawEnd);
    }

    private boolean is15MinuteAligned(OffsetDateTime dt) {
        return dt.getMinute() % 15 == 0 && dt.getSecond() == 0 && dt.getNano() == 0;
    }

    private record TimeWindow(OffsetDateTime startTime, OffsetDateTime endTime) {}
}
