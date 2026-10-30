package com.tanle.meeting_room_booking.booking.service;

import com.tanle.meeting_room_booking.booking.dto.*;
import com.tanle.meeting_room_booking.booking.entity.*;
import com.tanle.meeting_room_booking.booking.repository.BookingAttendeeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingAuditLogRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingChangeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.group.entity.UserGroupId;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.group.repository.UserGroupRepository;
import com.tanle.meeting_room_booking.group.service.GroupService;
import com.tanle.meeting_room_booking.issue.repository.RoomBlockRepository;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.service.RoomService;
import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.team.entity.UserTeamId;
import com.tanle.meeting_room_booking.team.repository.UserTeamRepository;
import com.tanle.meeting_room_booking.team.service.TeamService;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration CHECK_IN_GRACE_WINDOW = Duration.ofMinutes(10);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final OffsetDateTime MIN_BOUND = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_BOUND = OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private final BookingRepository bookingRepository;
    private final BookingChangeRepository bookingChangeRepository;
    private final BookingAttendeeRepository attendeeRepository;
    private final BookingAuditLogRepository auditLogRepository;
    private final RoomBlockRepository roomBlockRepository;
    private final UserTeamRepository userTeamRepository;
    private final UserGroupRepository userGroupRepository;
    private final TeamService teamService;
    private final GroupService groupService;
    private final NotificationService notificationService;
    private final RoomService roomService;
    private final UserRepo userRepo;

    /**
     * Gửi yêu cầu đặt phòng mới theo đúng Spec mục 8.1 & 8.2:
     * - Tạo booking ở trạng thái PENDING (chưa giữ phòng chính thức, lưu preferredRoom nếu có).
     * - Kiểm tra tư cách thành viên của người tổ chức đối với Team / Project (AC-10, AC-67).
     * - Kiểm tra quy tắc MeetingType và Visibility (SALARY_REVIEW, PERFORMANCE_REVIEW, INTERVIEW bắt buộc CONFIDENTIAL - POL-23, AC-72).
     * - Trả kèm cảnh báo PREFERRED_ROOM_BUSY hoặc NO_MATCHING_ROOM nếu phòng mong muốn đang bận (Spec 23.4).
     * - Thông báo cho các Admin hoạt động (trừ chính người gửi nếu người gửi là Admin - Spec 8.2).
     */
    @Transactional
    public BookingResponse createBooking(AuthUser actor, CreateBookingRequest rq) {
        OffsetDateTime start = rq.resolvedStartTime();
        OffsetDateTime end = rq.resolvedEndTime();
        validateTimeRange(start, end);

        MeetingType meetingType = rq.meetingType() != null ? rq.meetingType() : MeetingType.GENERAL;
        Visibility visibility = resolveAndValidateVisibility(meetingType, rq.visibility(), rq.isConfidential(), null);

        UUID teamId = rq.resolvedTeamId();
        UUID groupId = rq.resolvedGroupId();
        Team team = validateAndLoadOrganizerTeam(actor.id(), teamId);
        WorkGroup group = validateAndLoadOrganizerGroup(actor.id(), groupId);

        Map<UUID, AttendeeRole> participantRoles = resolveParticipantRoles(
                actor.id(),
                rq.resolvedParticipantIds(),
                rq.optionalAttendeeIds(),
                teamId,
                groupId,
                Boolean.TRUE.equals(rq.inviteAllScopeMembers())
        );

        int totalParticipants = rq.attendeeCount() != null
                ? Math.max(rq.attendeeCount(), participantRoles.size())
                : participantRoles.size();
        if (rq.attendeeCount() != null && rq.attendeeCount() < participantRoles.size()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tổng số người tham dự (attendeeCount) phải lớn hơn hoặc bằng số thành viên nội bộ trong danh sách mời (" + participantRoles.size() + ")"
            );
        }

        UUID preferredRoomId = rq.resolvedPreferredRoomId();
        Room preferredRoom = null;
        List<String> warnings = new ArrayList<>();
        if (preferredRoomId != null) {
            preferredRoom = roomService.findOrThrow(preferredRoomId);
            validateRoomForBooking(preferredRoom, totalParticipants);
            if (bookingRepository.existsOverlappingBooking(preferredRoom.getId(), start, end, null)
                    || roomBlockRepository.existsOverlappingActiveBlock(preferredRoom.getId(), start, end, null)) {
                warnings.add("PREFERRED_ROOM_BUSY: Phòng mong muốn hiện đang có lịch đặt hoặc đang trong khoảng chặn bảo trì. Yêu cầu vẫn được ghi nhận ở trạng thái PENDING để Admin bố trí phòng phù hợp.");
            }
        } else if (roomService.findAvailableRooms(start, end, totalParticipants, null).isEmpty()) {
            warnings.add("NO_MATCHING_ROOM: Hiện chưa có phòng trống đáp ứng sức chứa trong khung giờ này. Yêu cầu vẫn được ghi nhận ở trạng thái PENDING để Admin xem xét.");
        }

        Map<UUID, User> usersById = lockAndLoadActiveUsersOrThrow(participantRoles.keySet());
        User organizer = usersById.get(actor.id());

        List<UUID> relatedDepts = rq.relatedDepartmentIds() != null
                ? rq.relatedDepartmentIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();

        Booking booking = Booking.builder()
                .room(null) // PENDING chưa giữ phòng chính thức (POL-02, AC-01)
                .preferredRoom(preferredRoom)
                .organizer(organizer)
                .team(team)
                .group(group)
                .relatedDepartmentIds(relatedDepts)
                .title(rq.title().trim())
                .description(rq.resolvedPurpose())
                .note(rq.note() != null ? rq.note().trim() : null)
                .attendeeCount(totalParticipants)
                .meetingType(meetingType)
                .visibility(visibility)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.PENDING)
                .usageStatus(UsageStatus.NOT_APPLICABLE)
                .confidential(visibility == Visibility.CONFIDENTIAL)
                .meetingMode(rq.meetingMode() != null ? rq.meetingMode() : MeetingMode.STANDARD)
                .build();

        Booking saved = bookingRepository.saveAndFlush(booking);
        List<BookingAttendee> attendees = saveAttendees(saved, participantRoles, usersById);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(actor.id())
                .action("SUBMIT_PENDING")
                .details(Map.of(
                        "preferredRoomId", preferredRoom != null ? preferredRoom.getId().toString() : "",
                        "attendeeCount", totalParticipants,
                        "visibility", visibility.name()
                ))
                .build());

        // Thông báo cho các Admin hoạt động (trừ chính người gửi nếu người gửi cũng là Admin - Spec 8.2)
        notifyActiveAdminsExcept(
                actor.id(),
                saved,
                "BOOKING_SUBMITTED",
                "Yêu cầu đặt phòng mới chờ duyệt",
                String.format("%s gửi yêu cầu đặt phòng (%s - %s, %d người)",
                        organizer.getFullName(), saved.getStartTime(), saved.getEndTime(), totalParticipants),
                "SUBMIT:" + saved.getId() + ":v" + saved.getVersion()
        );

        return BookingResponse.from(saved, attendees, actor, warnings);
    }

    /**
     * Admin phê duyệt yêu cầu đặt phòng đang PENDING và phân bổ phòng chính thức (Spec 8.3, POL-01, AC-03..AC-09):
     * - Admin không được tự duyệt yêu cầu do chính mình tạo (SELF_APPROVAL_FORBIDDEN - AC-06).
     * - Kiểm tra expectedVersion (VERSION_CONFLICT - AC-07).
     * - Kiểm tra chưa tới giờ bắt đầu (nếu đã tới giờ bắt đầu thì chuyển EXPIRED - AC-08).
     * - Kiểm tra người tổ chức, thành viên tham dự, Team/Project còn hợp lệ (REFERENCE_NO_LONGER_VALID - AC-11, AC-75).
     * - Kiểm tra phòng được chọn đang ACTIVE, đủ sức chứa và không trùng lịch (AC-04, AC-09).
     * - Gán phòng chính thức (room), chuyển status = APPROVED, usageStatus = NOT_CHECKED_IN, tính checkInDeadline = min(startAt + 10 phút, endAt).
     */
    @Transactional
    public BookingResponse approveBooking(AuthUser admin, UUID bookingId, ApproveBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);

        if (booking.getOrganizer().getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }

        assertExpectedVersion(booking, rq.expectedVersion());

        OffsetDateTime now = OffsetDateTime.now();
        if (booking.getStatus() == BookingStatus.PENDING && !now.isBefore(booking.getStartTime())) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setUsageStatus(UsageStatus.NOT_APPLICABLE);
            bookingRepository.saveAndFlush(booking);
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Yêu cầu đặt phòng đã quá giờ bắt đầu mà chưa được duyệt và đã chuyển sang trạng thái EXPIRED");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể phê duyệt yêu cầu đang ở trạng thái PENDING (hiện tại: " + booking.getStatus() + ")");
        }

        // Kiểm tra lại tư cách Team / Project của người tổ chức tại thời điểm duyệt (AC-11, AC-75)
        if (booking.getTeam() != null) {
            boolean stillInTeam = userTeamRepository.existsById(new UserTeamId(booking.getOrganizer().getId(), booking.getTeam().getId()));
            if (!stillInTeam) {
                throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Người tổ chức không còn là thành viên của Team đã chọn");
            }
        }
        if (booking.getGroup() != null) {
            boolean stillInGroup = userGroupRepository.existsById(new UserGroupId(booking.getOrganizer().getId(), booking.getGroup().getId()));
            if (!stillInGroup) {
                throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Người tổ chức không còn là thành viên của Dự án / Phòng ban đã chọn");
            }
        }

        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);
        Set<UUID> participantIds = attendees.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        // Kiểm tra tất cả người tổ chức và người dự vẫn đang ACTIVE (Spec 8.3 & 21.2)
        try {
            lockAndLoadActiveUsersOrThrow(participantIds);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Một hoặc nhiều thành viên tham dự hoặc người tổ chức không còn hoạt động");
        }

        // Khóa bi quan dòng Room được chọn để chống 2 Admin duyệt 2 yêu cầu vào cùng 1 phòng đồng thời (AC-04)
        Room assignedRoom = roomService.findForUpdateOrThrow(rq.roomId());
        if (assignedRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_AVAILABLE_STATUS);
        }
        if (booking.getAttendeeCount() > assignedRoom.getCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_REQUIREMENTS_NOT_MET, "Phòng được chọn không đủ sức chứa cho " + booking.getAttendeeCount() + " người");
        }
        if (bookingRepository.existsOverlappingBooking(assignedRoom.getId(), booking.getStartTime(), booking.getEndTime(), booking.getId())
                || roomBlockRepository.existsOverlappingActiveBlock(assignedRoom.getId(), booking.getStartTime(), booking.getEndTime(), null)) {
            throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE);
        }

        OffsetDateTime deadline = computeCheckInDeadline(booking.getStartTime(), booking.getEndTime());

        booking.setRoom(assignedRoom);
        booking.setStatus(BookingStatus.APPROVED);
        booking.setUsageStatus(UsageStatus.NOT_CHECKED_IN);
        booking.setCheckInDeadline(deadline);
        booking.setApprovedBy(admin.id());
        booking.setApprovedAt(now);
        booking.setApprovalNote(rq.note() != null ? rq.note().trim() : null);

        Booking saved = bookingRepository.saveAndFlush(booking);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(admin.id())
                .action("APPROVE")
                .reason(booking.getApprovalNote())
                .details(Map.of(
                        "assignedRoomId", assignedRoom.getId().toString(),
                        "assignedRoomName", assignedRoom.getName(),
                        "checkInDeadline", deadline.toString()
                ))
                .build());

        // Thông báo cho người tổ chức và toàn bộ người tham dự (Spec 12.2 & AC-03)
        notificationService.sendBulk(
                participantIds,
                saved.getId(),
                "BOOKING_APPROVED",
                "Lịch đặt phòng đã được phê duyệt",
                String.format("Cuộc họp đã được bố trí tại phòng %s (%s - %s). Hạn check-in: %s.",
                        assignedRoom.getName(), saved.getStartTime(), saved.getEndTime(), deadline),
                Map.of("roomId", assignedRoom.getId().toString(), "roomName", assignedRoom.getName()),
                "APPROVE:" + saved.getId()
        );

        return BookingResponse.from(saved, attendees, admin);
    }

    /**
     * Admin từ chối yêu cầu đặt phòng đang PENDING (Spec 8.4):
     * - Chỉ Admin khác người tổ chức được phép từ chối.
     * - Lý do từ chối bắt buộc.
     */
    @Transactional
    public BookingResponse rejectBooking(AuthUser admin, UUID bookingId, RejectBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);

        if (booking.getOrganizer().getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }

        assertExpectedVersion(booking, rq.expectedVersion());

        OffsetDateTime now = OffsetDateTime.now();
        if (booking.getStatus() == BookingStatus.PENDING && !now.isBefore(booking.getStartTime())) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setUsageStatus(UsageStatus.NOT_APPLICABLE);
            bookingRepository.saveAndFlush(booking);
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Yêu cầu đặt phòng đã quá giờ bắt đầu (EXPIRED)");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể từ chối yêu cầu đang ở trạng thái PENDING");
        }

        String reason = rq.reason().trim();
        booking.setStatus(BookingStatus.REJECTED);
        booking.setUsageStatus(UsageStatus.NOT_APPLICABLE);
        booking.setRejectedBy(admin.id());
        booking.setRejectedAt(now);
        booking.setRejectReason(reason);

        Booking saved = bookingRepository.saveAndFlush(booking);
        // Hủy mọi đề nghị thay đổi đang PENDING của booking này (Spec 8.5, 9 & AC-17)
        bookingChangeRepository.cancelPendingChangesForBooking(bookingId, "Lịch đặt phòng gốc đã bị hủy", now);
        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(admin.id())
                .action("REJECT")
                .reason(reason)
                .details(Map.of())
                .build());

        notificationService.send(
                saved.getOrganizer().getId(),
                saved.getId(),
                "BOOKING_REJECTED",
                "Yêu cầu đặt phòng bị từ chối",
                String.format("Yêu cầu đặt phòng (%s - %s) đã bị từ chối. Lý do: %s",
                        saved.getStartTime(), saved.getEndTime(), reason),
                Map.of("reason", reason),
                "REJECT:" + saved.getId()
        );

        return BookingResponse.from(saved, attendees, admin);
    }

    /**
     * Admin chuyển phòng họp do vận hành / sự cố (Spec 11.2, 16 & 25.5: POST /api/v1/bookings/{id}/reassign-room):
     * - Giữ nguyên thời gian dự kiến, người dự, số người và trạng thái APPROVED.
     * - Phòng mới phải đang ACTIVE, đủ sức chứa và không trùng lịch.
     */
    @Transactional
    public BookingResponse reassignRoom(AuthUser admin, UUID bookingId, ReassignRoomRequest rq) {
        Booking booking = findOrThrow(bookingId);
        assertExpectedVersion(booking, rq.expectedVersion());

        if (booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể chuyển phòng cho lịch đã được duyệt (APPROVED) và chưa kết thúc");
        }
        if (booking.getUsageStatus() == UsageStatus.COMPLETED
                || booking.getUsageStatus() == UsageStatus.NO_SHOW
                || booking.getUsageStatus() == UsageStatus.INTERRUPTED) {
            throw new BusinessException(ErrorCode.USAGE_ALREADY_FINISHED, "Không thể chuyển phòng cho phiên sử dụng đã kết thúc");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!now.isBefore(booking.getEndTime())) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Cuộc họp đã hết giờ, không thể chuyển phòng");
        }

        Room previousRoom = booking.getRoom();
        Room newRoom = roomService.findForUpdateOrThrow(rq.roomId());
        if (newRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_AVAILABLE_STATUS);
        }
        if (booking.getAttendeeCount() > newRoom.getCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_REQUIREMENTS_NOT_MET, "Phòng mới không đủ sức chứa cho " + booking.getAttendeeCount() + " người");
        }

        OffsetDateTime checkFrom = now.isAfter(booking.getStartTime()) ? now : booking.getStartTime();
        if (bookingRepository.existsOverlappingBooking(newRoom.getId(), checkFrom, booking.getEndTime(), booking.getId())
                || roomBlockRepository.existsOverlappingActiveBlock(newRoom.getId(), checkFrom, booking.getEndTime(), null)) {
            throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE, "Phòng thay thế đang bận hoặc bị chặn trong khoảng thời gian còn lại");
        }

        booking.setRoom(newRoom);
        Booking saved = bookingRepository.saveAndFlush(booking);
        // Nếu có đề nghị thay đổi đang chờ (PENDING), tự động đóng với lý do lịch gốc đã được chuyển phòng vận hành (Spec 9 & AC-17)
        bookingChangeRepository.cancelPendingChangesForBooking(bookingId, "Lịch gốc đã được Admin chuyển phòng vận hành", now);
        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);

        String reason = rq.reason().trim();
        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(admin.id())
                .action("REASSIGN_ROOM")
                .reason(reason)
                .details(Map.of(
                        "fromRoomId", previousRoom != null ? previousRoom.getId().toString() : "",
                        "fromRoomName", previousRoom != null ? previousRoom.getName() : "",
                        "toRoomId", newRoom.getId().toString(),
                        "toRoomName", newRoom.getName()
                ))
                .build());

        Set<UUID> notifyIds = attendees.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        notificationService.sendBulk(
                notifyIds,
                saved.getId(),
                "BOOKING_ROOM_REASSIGNED",
                "Thông báo chuyển phòng họp",
                String.format("Cuộc họp (%s - %s) đã được chuyển sang phòng %s. Lý do: %s",
                        saved.getStartTime(), saved.getEndTime(), newRoom.getName(), reason),
                Map.of("newRoomId", newRoom.getId().toString(), "newRoomName", newRoom.getName(), "reason", reason),
                null
        );

        return BookingResponse.from(saved, attendees, admin);
    }

    public BookingResponse getById(AuthUser viewer, UUID id) {
        Booking booking = findOrThrow(id);
        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(id);
        return BookingResponse.from(booking, attendees, viewer);
    }

    public PageResponse<BookingResponse> getMySchedule(
            AuthUser viewer,
            UUID teamId,
            UUID groupId,
            BookingStatus status,
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            int page,
            int size) {
        OffsetDateTime effectiveFrom = fromTime != null ? fromTime : MIN_BOUND;
        OffsetDateTime effectiveTo = toTime != null ? toTime : MAX_BOUND;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian tìm kiếm không hợp lệ");
        }
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("startTime"), Sort.Order.asc("id"))
        );
        Page<Booking> bookingsPage = bookingRepository.findMySchedule(viewer.id(), teamId, groupId, status, effectiveFrom, effectiveTo, pageable);
        return toPageResponse(bookingsPage, viewer);
    }

    public PageResponse<BookingResponse> search(
            AuthUser viewer,
            UUID roomId,
            UUID organizerId,
            UUID teamId,
            UUID groupId,
            BookingStatus status,
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            int page,
            int size) {
        OffsetDateTime effectiveFrom = fromTime != null ? fromTime : MIN_BOUND;
        OffsetDateTime effectiveTo = toTime != null ? toTime : MAX_BOUND;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian tìm kiếm không hợp lệ");
        }
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );
        Page<Booking> bookingsPage = bookingRepository.searchBookings(roomId, organizerId, teamId, groupId, status, effectiveFrom, effectiveTo, pageable);
        return toPageResponse(bookingsPage, viewer);
    }

    public List<BookingResponse> getRoomSchedule(AuthUser viewer, UUID roomId, OffsetDateTime fromTime, OffsetDateTime toTime) {
        if (fromTime == null || toTime == null || !fromTime.isBefore(toTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian truy vấn lịch không hợp lệ");
        }
        roomService.findOrThrow(roomId);
        List<Booking> bookings = bookingRepository.findRoomSchedule(roomId, fromTime, toTime);
        Map<UUID, List<BookingAttendee>> attendeesByBooking = loadAttendeesMap(bookings);
        return bookings.stream()
                .map(b -> BookingResponse.from(b, attendeesByBooking.getOrDefault(b.getId(), List.of()), viewer))
                .toList();
    }

    /**
     * Chỉnh sửa yêu cầu đặt phòng:
     * - Theo Spec 4.2 & 8.2: Chủ yêu cầu được phép sửa yêu cầu PENDING trước giờ bắt đầu (Admin không sửa thay).
     * - Không cho phép hạ mức riêng tư từ CONFIDENTIAL về STANDARD (Spec 27.4 & AC-72).
     */
    @Transactional
    public BookingResponse updateBooking(AuthUser actor, UUID bookingId, UpdateBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);
        assertExpectedVersion(booking, rq.expectedVersion());

        boolean isOrganizer = booking.getOrganizer().getId().equals(actor.id());
        if (!isOrganizer && !actor.hasAuthority("BOOKING_MANAGE")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (booking.getStatus() == BookingStatus.PENDING && !isOrganizer) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người tạo yêu cầu mới được phép chỉnh sửa yêu cầu đang chờ duyệt (PENDING)");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!now.isBefore(booking.getStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Không thể chỉnh sửa yêu cầu khi đã tới hoặc qua giờ bắt đầu");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Không thể chỉnh sửa lịch ở trạng thái " + booking.getStatus());
        }

        OffsetDateTime start = rq.resolvedStartTime() != null ? rq.resolvedStartTime() : booking.getStartTime();
        OffsetDateTime end = rq.resolvedEndTime() != null ? rq.resolvedEndTime() : booking.getEndTime();
        validateTimeRange(start, end);

        MeetingType targetMeetingType = rq.meetingType() != null ? rq.meetingType() : booking.getMeetingType();
        Visibility targetVisibility = resolveAndValidateVisibility(targetMeetingType, rq.visibility(), rq.isConfidential(), booking.getVisibility());

        UUID teamId = rq.resolvedTeamId();
        Team targetTeam = teamId != null
                ? validateAndLoadOrganizerTeam(booking.getOrganizer().getId(), teamId)
                : booking.getTeam();

        UUID groupId = rq.resolvedGroupId();
        WorkGroup targetGroup = groupId != null
                ? validateAndLoadOrganizerGroup(booking.getOrganizer().getId(), groupId)
                : booking.getGroup();

        List<BookingAttendee> currentAttendees = attendeeRepository.findById_BookingId(bookingId);
        Map<UUID, BookingAttendee> existingByUserId = currentAttendees.stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));

        boolean updateParticipants = rq.resolvedParticipantIds() != null
                || rq.optionalAttendeeIds() != null
                || Boolean.TRUE.equals(rq.inviteAllScopeMembers());

        Map<UUID, AttendeeRole> participantRoles;
        if (updateParticipants) {
            participantRoles = resolveParticipantRoles(
                    booking.getOrganizer().getId(),
                    rq.resolvedParticipantIds(),
                    rq.optionalAttendeeIds(),
                    targetTeam != null ? targetTeam.getId() : null,
                    targetGroup != null ? targetGroup.getId() : null,
                    Boolean.TRUE.equals(rq.inviteAllScopeMembers())
            );
        } else {
            participantRoles = currentAttendees.stream()
                    .collect(Collectors.toMap(a -> a.getUser().getId(), BookingAttendee::getAttendeeRole, (a, b) -> a, LinkedHashMap::new));
        }

        int totalParticipants = rq.attendeeCount() != null
                ? Math.max(rq.attendeeCount(), participantRoles.size())
                : Math.max(booking.getAttendeeCount(), participantRoles.size());
        if (rq.attendeeCount() != null && rq.attendeeCount() < participantRoles.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "attendeeCount không được nhỏ hơn số người tham dự nội bộ");
        }

        List<String> warnings = new ArrayList<>();
        UUID preferredRoomId = rq.resolvedPreferredRoomId();
        if (preferredRoomId != null) {
            Room prefRoom = roomService.findOrThrow(preferredRoomId);
            validateRoomForBooking(prefRoom, totalParticipants);
            booking.setPreferredRoom(prefRoom);
            if (booking.getStatus() == BookingStatus.PENDING
                    && bookingRepository.existsOverlappingBooking(prefRoom.getId(), start, end, booking.getId())) {
                warnings.add("PREFERRED_ROOM_BUSY: Phòng mong muốn hiện đang bận trong khung giờ mới.");
            }
        }

        if (booking.getRoom() != null) {
            validateRoomForBooking(booking.getRoom(), totalParticipants);
            if (bookingRepository.existsOverlappingBooking(booking.getRoom().getId(), start, end, booking.getId())) {
                throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE);
            }
        }

        Map<UUID, User> usersById = lockAndLoadActiveUsersOrThrow(participantRoles.keySet());

        boolean timeChanged = !booking.getStartTime().isEqual(start) || !booking.getEndTime().isEqual(end);
        booking.setTeam(targetTeam);
        booking.setGroup(targetGroup);
        if (rq.relatedDepartmentIds() != null) {
            booking.setRelatedDepartmentIds(rq.relatedDepartmentIds().stream().filter(Objects::nonNull).distinct().toList());
        }
        booking.setTitle(rq.title().trim());
        if (rq.resolvedPurpose() != null) {
            booking.setDescription(rq.resolvedPurpose());
        }
        if (rq.note() != null) {
            booking.setNote(rq.note().trim());
        }
        booking.setAttendeeCount(totalParticipants);
        booking.setMeetingType(targetMeetingType);
        booking.setVisibility(targetVisibility);
        booking.setConfidential(targetVisibility == Visibility.CONFIDENTIAL);
        booking.setStartTime(start);
        booking.setEndTime(end);
        if (booking.getStatus() == BookingStatus.APPROVED || booking.getStatus() == BookingStatus.CONFIRMED) {
            booking.setCheckInDeadline(computeCheckInDeadline(start, end));
        }

        Booking saved = bookingRepository.saveAndFlush(booking);

        Set<UUID> newlyAddedUserIds = new LinkedHashSet<>();
        List<BookingAttendee> finalAttendees = syncAttendeesDiff(
                saved,
                existingByUserId,
                participantRoles,
                usersById,
                timeChanged,
                newlyAddedUserIds
        );

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(actor.id())
                .action("UPDATE_BOOKING")
                .details(Map.of("version", saved.getVersion()))
                .build());

        if (saved.getStatus() == BookingStatus.PENDING) {
            notifyActiveAdminsExcept(
                    actor.id(),
                    saved,
                    "BOOKING_UPDATED_PENDING",
                    "Yêu cầu đặt phòng chờ duyệt vừa được cập nhật",
                    String.format("Yêu cầu đặt phòng (%s - %s) vừa được người tổ chức cập nhật (version %d).",
                            saved.getStartTime(), saved.getEndTime(), saved.getVersion()),
                    "UPDATE_PENDING:" + saved.getId() + ":v" + saved.getVersion()
            );
        }

        return BookingResponse.from(saved, finalAttendees, actor, warnings);
    }

    @Transactional
    public BookingResponse respondRsvp(AuthUser actor, UUID bookingId, RsvpRequest rq) {
        if (rq.status() == RsvpStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái RSVP phải là ACCEPTED hoặc DECLINED");
        }
        Booking booking = findOrThrow(bookingId);
        if (booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể phản hồi RSVP khi lịch họp chưa kết thúc/hủy");
        }

        lockAndLoadActiveUsersOrThrow(Set.of(actor.id()));

        BookingAttendee attendee = attendeeRepository.findById(new BookingAttendeeId(bookingId, actor.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Bạn không nằm trong danh sách tham dự cuộc họp này"));

        if (attendee.getAttendeeRole() == AttendeeRole.ORGANIZER) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Người tổ chức không thể từ chối cuộc họp của chính mình (hãy hủy lịch nếu cần)");
        }

        attendee.setRsvpStatus(rq.status());
        attendee.setDeclineReason(rq.status() == RsvpStatus.DECLINED && rq.declineReason() != null
                ? rq.declineReason().trim()
                : null);
        attendee.setRespondedAt(OffsetDateTime.now());
        attendeeRepository.saveAndFlush(attendee);

        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);
        return BookingResponse.from(booking, attendees, actor);
    }

    /**
     * Hủy lịch đặt phòng theo đúng Spec mục 8.5, 25.2 & AC-18, AC-19, AC-62:
     * - Chủ yêu cầu được hủy PENDING hoặc APPROVED trước startAt (sau startAt bị từ chối - AC-19).
     * - Admin được hủy PENDING hoặc APPROVED trước endAt vì vận hành.
     * - Bắt buộc có lý do hủy (reason).
     * - Nếu đang IN_USE mà Admin hủy: chuyển usageStatus = INTERRUPTED, đóng phiên tại thời điểm hủy và giải phóng phần thời gian còn lại (releasedAt = now) mà vẫn giữ lịch sử thời gian đã sử dụng (AC-62).
     */
    @Transactional
    public BookingResponse cancelBooking(AuthUser actor, UUID bookingId, CancelBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);
        assertOrganizerOrManager(actor, booking);

        if (rq != null) {
            assertExpectedVersion(booking, rq.expectedVersion());
        }

        // Hủy lại một booking đã CANCELLED trả trạng thái hiện có (Spec 8.5)
        if (booking.getStatus() == BookingStatus.CANCELLED && booking.getUsageStatus() != UsageStatus.NO_SHOW) {
            List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);
            return BookingResponse.from(booking, attendees, actor);
        }

        if (booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.EXPIRED
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getUsageStatus() == UsageStatus.COMPLETED
                || booking.getUsageStatus() == UsageStatus.INTERRUPTED
                || booking.getUsageStatus() == UsageStatus.NO_SHOW) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể hủy lịch đặt phòng ở trạng thái " + booking.getStatus() + " / " + booking.getUsageStatus()
            );
        }

        String reason = rq != null && rq.reason() != null && !rq.reason().isBlank() ? rq.reason().trim() : null;
        if (reason == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hủy lịch đặt phòng là bắt buộc");
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean isOrganizer = booking.getOrganizer().getId().equals(actor.id());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(actor.role()) || actor.hasAuthority("BOOKING_MANAGE");

        // Chủ lịch chỉ được hủy trước giờ bắt đầu (AC-18, AC-19); sau khi bắt đầu chỉ Admin mới được hủy trước khi kết thúc
        if (!now.isBefore(booking.getStartTime())) {
            if (!isAdmin || isOrganizer && !actor.hasAuthority("BOOKING_MANAGE")) {
                throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chủ lịch chỉ được phép hủy trước giờ bắt đầu cuộc họp");
            }
            if (!now.isBefore(booking.getEndTime())) {
                throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Cuộc họp đã qua giờ kết thúc, không thể hủy");
            }
        }

        BookingStatus previousStatus = booking.getStatus();
        boolean wasInUse = booking.getUsageStatus() == UsageStatus.IN_USE || booking.getStatus() == BookingStatus.IN_PROGRESS;

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledBy(actor.id());
        booking.setCancelledAt(now);
        booking.setCancelReason(reason);
        booking.setCancellationReasonCode(isOrganizer ? "USER_CANCELLED" : "ADMIN_CANCELLED");

        if (wasInUse) {
            booking.setUsageStatus(UsageStatus.INTERRUPTED);
            booking.setActualEndTime(now);
            booking.setEndSource("ADMIN_CANCELLATION");
            booking.setReleasedAt(now);
        } else {
            booking.setUsageStatus(UsageStatus.NOT_APPLICABLE);
            booking.setReleasedAt(now.isBefore(booking.getStartTime()) ? booking.getStartTime() : now);
        }

        Booking saved = bookingRepository.saveAndFlush(booking);
        // Hủy mọi đề nghị thay đổi đang PENDING của booking này (Spec 8.5, 9 & AC-17)
        bookingChangeRepository.cancelPendingChangesForBooking(bookingId, "Lịch đặt phòng gốc đã bị hủy", now);
        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(actor.id())
                .action(wasInUse ? "ADMIN_INTERRUPT_CANCEL" : (isOrganizer ? "USER_CANCEL" : "ADMIN_CANCEL"))
                .reason(reason)
                .details(Map.of("previousStatus", previousStatus.name()))
                .build());

        if (previousStatus == BookingStatus.PENDING) {
            // Hủy PENDING: chưa thông báo người tham dự vì chưa có lịch chính thức (Spec 8.5)
            if (!isOrganizer) {
                notificationService.send(
                        saved.getOrganizer().getId(),
                        saved.getId(),
                        "BOOKING_CANCELLED",
                        "Yêu cầu đặt phòng đã bị hủy",
                        String.format("Yêu cầu đặt phòng (%s - %s) đã bị quản trị viên hủy. Lý do: %s",
                                saved.getStartTime(), saved.getEndTime(), reason),
                        Map.of("reason", reason),
                        "CANCEL:" + saved.getId()
                );
            }
        } else {
            Set<UUID> notifyUserIds = attendees.stream()
                    .map(a -> a.getUser().getId())
                    .filter(id -> !id.equals(actor.id()))
                    .collect(Collectors.toSet());
            notificationService.sendBulk(
                    notifyUserIds,
                    saved.getId(),
                    "BOOKING_CANCELLED",
                    "Cuộc họp đã bị hủy",
                    String.format("Cuộc họp tại phòng %s (%s - %s) đã bị hủy (Lý do: %s)",
                            saved.getRoom() != null ? saved.getRoom().getName() : "N/A",
                            saved.getStartTime(),
                            saved.getEndTime(),
                            reason),
                    Map.of("reason", reason),
                    "CANCEL:" + saved.getId()
            );
        }

        return BookingResponse.from(saved, attendees, actor);
    }

    /**
     * Check-in nhận phòng theo đúng Spec mục 25.1, 25.3 & AC-47..AC-54:
     * - Cửa sổ hợp lệ: startAt <= serverNow < checkInDeadline, trong đó checkInDeadline = min(startAt + 10 phút, endAt).
     * - Không cho phép check-in trước startAt (CHECK_IN_TOO_EARLY - AC-47).
     * - Nếu serverNow >= checkInDeadline: tự động chuyển sang CANCELLED + NO_SHOW, giải phóng phòng tại deadline và ném CHECK_IN_DEADLINE_PASSED (AC-50, AC-51).
     * - Quyền check-in: Người tổ chức hoặc bất kỳ người tham dự nội bộ ACTIVE nào của cuộc họp (hoặc Admin vận hành kèm lý do) (AC-48, AC-49).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public BookingResponse checkIn(AuthUser actor, UUID bookingId, UsageActionRequest rq) {
        Booking booking = findOrThrow(bookingId);
        if (rq != null) {
            assertExpectedVersion(booking, rq.expectedVersion());
        }

        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);

        // Idempotent retry: nếu đã IN_USE thì trả lại kết quả hiện có (Spec 25.3 & AC-54)
        if (booking.getUsageStatus() == UsageStatus.IN_USE || booking.getStatus() == BookingStatus.IN_PROGRESS) {
            return BookingResponse.from(booking, attendees, actor);
        }
        if (booking.getUsageStatus() == UsageStatus.COMPLETED || booking.getUsageStatus() == UsageStatus.INTERRUPTED) {
            throw new BusinessException(ErrorCode.USAGE_ALREADY_FINISHED);
        }
        if (booking.getUsageStatus() == UsageStatus.NO_SHOW || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new BusinessException(ErrorCode.CHECK_IN_DEADLINE_PASSED);
        }
        if (booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể check-in lịch đặt phòng đã được phê duyệt (APPROVED)");
        }

        boolean isParticipant = booking.getOrganizer().getId().equals(actor.id())
                || attendees.stream().anyMatch(a -> a.getUser().getId().equals(actor.id()));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(actor.role()) || actor.hasAuthority("BOOKING_MANAGE");

        if (!isParticipant && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người tổ chức, thành viên tham dự hoặc quản trị viên mới được phép check-in");
        }
        if (!isParticipant && (rq == null || rq.reason() == null || rq.reason().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quản trị viên xác nhận check-in vận hành thay mặt cuộc họp phải cung cấp lý do (reason)");
        }

        Room lockedRoom = roomService.findForUpdateOrThrow(booking.getRoom().getId());
        if (lockedRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_AVAILABLE_STATUS);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(booking.getStartTime())) {
            throw new BusinessException(ErrorCode.CHECK_IN_TOO_EARLY);
        }

        OffsetDateTime deadline = booking.getCheckInDeadline() != null
                ? booking.getCheckInDeadline()
                : computeCheckInDeadline(booking.getStartTime(), booking.getEndTime());

        if (!now.isBefore(deadline)) {
            // Quá hạn check-in: tự động chuyển CANCELLED + NO_SHOW và giải phóng phòng tại deadline ngay trong giao dịch (Spec 25.3 & AC-50, AC-51)
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setUsageStatus(UsageStatus.NO_SHOW);
            booking.setCancellationReasonCode("NO_SHOW");
            booking.setCancelReason("Quá thời hạn check-in (" + deadline + ")");
            booking.setCancelledAt(deadline);
            booking.setReleasedAt(deadline);
            bookingRepository.saveAndFlush(booking);
            throw new BusinessException(ErrorCode.CHECK_IN_DEADLINE_PASSED);
        }

        if (bookingRepository.existsInProgressBookingInRoom(lockedRoom.getId(), booking.getId())) {
            throw new BusinessException(
                    ErrorCode.ROOM_TIME_CONFLICT,
                    "Phòng họp hiện vẫn đang có cuộc họp khác diễn ra (chưa check-out)"
            );
        }

        String evidenceType = isParticipant ? "USER_CONFIRMATION" : "ADMIN_CONFIRMATION";
        booking.setUsageStatus(UsageStatus.IN_USE);
        booking.setActualStartTime(now);
        booking.setCheckedInBy(actor.id());
        booking.setEvidenceType(evidenceType);

        Booking saved = bookingRepository.saveAndFlush(booking);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(actor.id())
                .action("CHECK_IN")
                .reason(rq != null ? rq.reason() : null)
                .details(Map.of("evidenceType", evidenceType, "checkedInAt", now.toString()))
                .build());

        Set<UUID> notifyIds = attendees.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        notificationService.sendBulk(
                notifyIds,
                saved.getId(),
                "BOOKING_CHECKED_IN",
                "Đã check-in nhận phòng họp",
                String.format("%s đã xác nhận check-in cuộc họp tại phòng %s lúc %s.",
                        actor.email(), lockedRoom.getName(), now),
                Map.of("checkedInBy", actor.id().toString(), "evidenceType", evidenceType),
                "CHECKIN:" + saved.getId()
        );

        return BookingResponse.from(saved, attendees, actor);
    }

    /**
     * Check-out trả phòng theo đúng Spec mục 25.4 & AC-55..AC-59:
     * - Chỉ Chủ lịch, người đã check-in hoặc Admin (kèm lý do) mới được phép check-out (AC-55, AC-56).
     * - Giữ nguyên startTime / endTime dự kiến của booking; ghi nhận releasedAt = now để giải phóng sớm phần thời gian còn lại (AC-55, AC-64).
     * - Nếu đã tới/qua endTime thì tự động đóng với SYSTEM_SCHEDULED_END và cảnh báo AUTO_ENDED (AC-57, AC-58).
     */
    @Transactional
    public BookingResponse checkOut(AuthUser actor, UUID bookingId, UsageActionRequest rq) {
        Booking booking = findOrThrow(bookingId);
        if (rq != null) {
            assertExpectedVersion(booking, rq.expectedVersion());
        }

        List<BookingAttendee> attendees = attendeeRepository.findById_BookingId(bookingId);

        if (booking.getUsageStatus() != UsageStatus.IN_USE && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.USAGE_NOT_ACTIVE, "Chỉ có thể check-out khi cuộc họp đang ở trạng thái IN_USE");
        }

        boolean isOrganizerOrChecker = booking.getOrganizer().getId().equals(actor.id())
                || (booking.getCheckedInBy() != null && booking.getCheckedInBy().equals(actor.id()));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(actor.role()) || actor.hasAuthority("BOOKING_MANAGE");

        if (!isOrganizerOrChecker && !isAdmin) {
            throw new BusinessException(ErrorCode.CHECK_OUT_FORBIDDEN);
        }
        if (!isOrganizerOrChecker && (rq == null || rq.reason() == null || rq.reason().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quản trị viên can thiệp check-out phải cung cấp lý do (reason)");
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<String> warnings = new ArrayList<>();

        if (!now.isBefore(booking.getEndTime())) {
            // Đã tới hoặc qua endTime: đóng phiên theo giờ dự kiến (SYSTEM_SCHEDULED_END) kèm cảnh báo AUTO_ENDED (Spec 25.4 & AC-58)
            booking.setUsageStatus(UsageStatus.COMPLETED);
            booking.setEndSource("SYSTEM_SCHEDULED_END");
            warnings.add("AUTO_ENDED: Phiên sử dụng đã tự động kết thúc tại thời điểm hết giờ dự kiến (" + booking.getEndTime() + ").");
            Booking saved = bookingRepository.saveAndFlush(booking);
            return BookingResponse.from(saved, attendees, actor, warnings);
        }

        OffsetDateTime actualEnd = now.isAfter(booking.getActualStartTime()) ? now : booking.getActualStartTime().plusSeconds(1);
        String endSource = isOrganizerOrChecker ? "USER_CHECKOUT" : "ADMIN_CHECKOUT";

        booking.setUsageStatus(UsageStatus.COMPLETED);
        booking.setActualEndTime(actualEnd);
        booking.setCheckedOutBy(actor.id());
        booking.setEndSource(endSource);
        booking.setReleasedAt(actualEnd); // Giải phóng phần thời gian còn lại mà KHÔNG ghi đè endTime dự kiến (Spec 25.4 & 25.5)

        Booking saved = bookingRepository.saveAndFlush(booking);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(saved.getId())
                .actorId(actor.id())
                .action("CHECK_OUT")
                .reason(rq != null ? rq.reason() : null)
                .details(Map.of("endSource", endSource, "releasedAt", actualEnd.toString()))
                .build());

        Set<UUID> notifyIds = attendees.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        notificationService.sendBulk(
                notifyIds,
                saved.getId(),
                "BOOKING_CHECKED_OUT",
                "Đã check-out trả phòng họp",
                String.format("%s đã check-out cuộc họp tại phòng %s lúc %s.",
                        actor.email(), saved.getRoom() != null ? saved.getRoom().getName() : "N/A", actualEnd),
                Map.of("checkedOutBy", actor.id().toString(), "endSource", endSource, "releasedAt", actualEnd.toString()),
                "CHECKOUT:" + saved.getId()
        );

        return BookingResponse.from(saved, attendees, actor, warnings);
    }

    public List<BookingAuditLog> getAuditLogs(AuthUser viewer, UUID bookingId) {
        Booking booking = findOrThrow(bookingId);
        boolean isOrganizer = booking.getOrganizer().getId().equals(viewer.id());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(viewer.role()) || viewer.hasAuthority("BOOKING_MANAGE");
        if (!isOrganizer && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ chủ lịch hoặc Quản trị viên mới được phép xem lịch sử thao tác của booking");
        }
        return auditLogRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    // ----- Đề nghị thay đổi lịch đã duyệt (BookingChange - Spec mục 9 & AC-14..AC-17) -----

    /**
     * Chủ lịch tạo đề nghị thay đổi (BookingChange) cho một lịch đã được duyệt (APPROVED) trước giờ bắt đầu (Spec 9 & AC-14):
     * - Lịch gốc vẫn giữ nguyên trạng thái APPROVED và vẫn giữ phòng cũ.
     * - Mỗi booking chỉ có tối đa 1 đề nghị PENDING (PENDING_CHANGE_EXISTS).
     * - Mức riêng tư chỉ được giữ nguyên hoặc nâng từ STANDARD lên CONFIDENTIAL, không được hạ ngược (AC-72).
     */
    @Transactional
    public BookingChangeResponse createBookingChange(AuthUser actor, UUID bookingId, CreateBookingChangeRequest rq) {
        Booking booking = findOrThrow(bookingId);

        if (!booking.getOrganizer().getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ chủ lịch (người tổ chức) mới được phép gửi đề nghị thay đổi lịch đã duyệt");
        }
        if (booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể gửi đề nghị thay đổi cho lịch đã được phê duyệt (APPROVED)");
        }
        if (booking.getUsageStatus() != UsageStatus.NOT_CHECKED_IN) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Không thể gửi đề nghị thay đổi khi cuộc họp đã check-in hoặc kết thúc");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!now.isBefore(booking.getStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ được gửi đề nghị thay đổi trước giờ bắt đầu của lịch gốc");
        }

        assertExpectedVersion(booking, rq.resolvedBaseVersion());

        if (bookingChangeRepository.existsByBooking_IdAndStatus(bookingId, BookingChangeStatus.PENDING)) {
            throw new BusinessException(ErrorCode.PENDING_CHANGE_EXISTS);
        }

        OffsetDateTime newStart = rq.resolvedStartTime() != null ? rq.resolvedStartTime() : booking.getStartTime();
        OffsetDateTime newEnd = rq.resolvedEndTime() != null ? rq.resolvedEndTime() : booking.getEndTime();
        validateTimeRange(newStart, newEnd);

        MeetingType newMeetingType = rq.meetingType() != null ? rq.meetingType() : booking.getMeetingType();
        Visibility newVisibility = resolveAndValidateVisibility(newMeetingType, rq.visibility(), rq.isConfidential(), booking.getVisibility());

        UUID teamId = rq.resolvedTeamId();
        Team targetTeam = teamId != null
                ? validateAndLoadOrganizerTeam(actor.id(), teamId)
                : booking.getTeam();

        UUID groupId = rq.resolvedGroupId();
        WorkGroup targetGroup = groupId != null
                ? validateAndLoadOrganizerGroup(actor.id(), groupId)
                : booking.getGroup();

        Set<UUID> requestedParticipants = rq.resolvedParticipantIds();
        List<UUID> participantList;
        if (requestedParticipants != null) {
            Set<UUID> merged = new LinkedHashSet<>(requestedParticipants);
            merged.add(actor.id());
            lockAndLoadActiveUsersOrThrow(merged);
            participantList = new ArrayList<>(merged);
        } else {
            participantList = attendeeRepository.findById_BookingId(bookingId).stream()
                    .map(a -> a.getUser().getId())
                    .distinct()
                    .toList();
        }

        int newAttendeeCount = rq.attendeeCount() != null
                ? Math.max(rq.attendeeCount(), participantList.size())
                : Math.max(booking.getAttendeeCount(), participantList.size());
        if (rq.attendeeCount() != null && rq.attendeeCount() < participantList.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "attendeeCount không được nhỏ hơn số người tham dự nội bộ");
        }

        UUID prefRoomId = rq.resolvedPreferredRoomId();
        Room prefRoom = prefRoomId != null ? roomService.findOrThrow(prefRoomId) : booking.getRoom();
        if (prefRoom != null) {
            validateRoomForBooking(prefRoom, newAttendeeCount);
        }

        List<UUID> relatedDepts = rq.relatedDepartmentIds() != null
                ? rq.relatedDepartmentIds().stream().filter(Objects::nonNull).distinct().toList()
                : booking.getRelatedDepartmentIds();

        BookingChange change = BookingChange.builder()
                .booking(booking)
                .baseVersion(booking.getVersion())
                .title(rq.title().trim())
                .purpose(rq.resolvedPurpose() != null ? rq.resolvedPurpose() : booking.getDescription())
                .note(rq.note() != null ? rq.note().trim() : booking.getNote())
                .meetingType(newMeetingType)
                .visibility(newVisibility)
                .startTime(newStart)
                .endTime(newEnd)
                .attendeeCount(newAttendeeCount)
                .preferredRoom(prefRoom)
                .team(targetTeam)
                .group(targetGroup)
                .relatedDepartmentIds(relatedDepts != null ? relatedDepts : List.of())
                .participantIds(participantList)
                .status(BookingChangeStatus.PENDING)
                .build();

        BookingChange saved = bookingChangeRepository.saveAndFlush(change);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(booking.getId())
                .actorId(actor.id())
                .action("SUBMIT_CHANGE")
                .details(Map.of("changeId", saved.getId().toString(), "baseVersion", booking.getVersion()))
                .build());

        notifyActiveAdminsExcept(
                actor.id(),
                booking,
                "BOOKING_CHANGE_SUBMITTED",
                "Đề nghị thay đổi lịch họp chờ duyệt",
                String.format("Có đề nghị thay đổi lịch họp (%s -> %s, %d người) đang chờ phê duyệt.",
                        newStart, newEnd, newAttendeeCount),
                "CHANGE_SUBMIT:" + saved.getId()
        );

        return BookingChangeResponse.from(saved, actor);
    }

    public List<BookingChangeResponse> getBookingChanges(AuthUser viewer, UUID bookingId) {
        findOrThrow(bookingId);
        return bookingChangeRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream()
                .map(bc -> BookingChangeResponse.from(bc, viewer))
                .toList();
    }

    /**
     * Admin phê duyệt đề nghị thay đổi (BookingChange) theo Spec mục 9 & AC-15, AC-16, AC-17:
     * - Admin không được tự duyệt đề nghị của chính mình (SELF_APPROVAL_FORBIDDEN).
     * - Kiểm tra booking gốc còn APPROVED, chưa bắt đầu, đúng baseVersion và belum check-in.
     * - Kiểm tra phòng mới (roomId) đủ sức chứa, không trùng lịch khác (bỏ qua chính booking gốc) và không giao RoomBlock.
     * - Cập nhật nguyên tử booking gốc + đóng đề nghị sang APPROVED + thông báo cho hợp của người dự cũ và mới.
     */
    @Transactional
    public BookingChangeResponse approveBookingChange(AuthUser admin, UUID changeId, ApproveBookingRequest rq) {
        BookingChange change = bookingChangeRepository.findWithDetailsById(changeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đề nghị thay đổi lịch"));

        Booking booking = change.getBooking();
        if (booking.getOrganizer().getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if ( rq.expectedVersion() != null && change.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (change.getStatus() == BookingChangeStatus.PENDING
                && (!now.isBefore(booking.getStartTime()) || !now.isBefore(change.getStartTime()))) {
            change.setStatus(BookingChangeStatus.EXPIRED);
            change.setProcessedAt(now);
            change.setProcessReason("Đề nghị đã quá giờ bắt đầu");
            bookingChangeRepository.saveAndFlush(change);
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Đề nghị thay đổi đã hết hạn (EXPIRED) do tới giờ bắt đầu");
        }

        if (change.getStatus() != BookingChangeStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Đề nghị thay đổi không còn ở trạng thái PENDING");
        }
        if ((booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED)
                || booking.getUsageStatus() != UsageStatus.NOT_CHECKED_IN) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Lịch gốc không còn ở trạng thái hợp lệ để áp dụng thay đổi");
        }
        if (booking.getVersion() != change.getBaseVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "Lịch gốc đã thay đổi phiên bản so với thời điểm tạo đề nghị");
        }

        // Kiểm tra lại tư cách Team / Project của người tổ chức tại thời điểm duyệt thay đổi (AC-75)
        if (change.getTeam() != null && !userTeamRepository.existsById(new UserTeamId(booking.getOrganizer().getId(), change.getTeam().getId()))) {
            throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Người tổ chức không còn thuộc Team trong đề nghị");
        }
        if (change.getGroup() != null && !userGroupRepository.existsById(new UserGroupId(booking.getOrganizer().getId(), change.getGroup().getId()))) {
            throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Người tổ chức không còn thuộc Dự án trong đề nghị");
        }

        Set<UUID> newParticipantSet = new LinkedHashSet<>(change.getParticipantIds());
        newParticipantSet.add(booking.getOrganizer().getId());
        Map<UUID, User> usersById;
        try {
            usersById = lockAndLoadActiveUsersOrThrow(newParticipantSet);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.REFERENCE_NO_LONGER_VALID, "Một hoặc nhiều thành viên trong đề nghị không còn hoạt động");
        }

        Room targetRoom = roomService.findForUpdateOrThrow(rq.roomId());
        if (targetRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_AVAILABLE_STATUS);
        }
        if (change.getAttendeeCount() > targetRoom.getCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_REQUIREMENTS_NOT_MET, "Phòng được chọn không đủ sức chứa cho " + change.getAttendeeCount() + " người");
        }
        if (bookingRepository.existsOverlappingBooking(targetRoom.getId(), change.getStartTime(), change.getEndTime(), booking.getId())
                || roomBlockRepository.existsOverlappingActiveBlock(targetRoom.getId(), change.getStartTime(), change.getEndTime(), null)) {
            throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE);
        }

        List<BookingAttendee> oldAttendees = attendeeRepository.findById_BookingId(booking.getId());
        Map<UUID, BookingAttendee> existingByUserId = oldAttendees.stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));

        boolean timeChanged = !booking.getStartTime().isEqual(change.getStartTime())
                || !booking.getEndTime().isEqual(change.getEndTime());

        booking.setRoom(targetRoom);
        booking.setPreferredRoom(change.getPreferredRoom());
        booking.setTeam(change.getTeam());
        booking.setGroup(change.getGroup());
        booking.setRelatedDepartmentIds(change.getRelatedDepartmentIds() != null ? new ArrayList<>(change.getRelatedDepartmentIds()) : new ArrayList<>());
        booking.setTitle(change.getTitle());
        booking.setDescription(change.getPurpose());
        booking.setNote(change.getNote());
        booking.setMeetingType(change.getMeetingType());
        booking.setVisibility(change.getVisibility());
        booking.setConfidential(change.getVisibility() == Visibility.CONFIDENTIAL);
        booking.setStartTime(change.getStartTime());
        booking.setEndTime(change.getEndTime());
        booking.setAttendeeCount(change.getAttendeeCount());
        booking.setCheckInDeadline(computeCheckInDeadline(change.getStartTime(), change.getEndTime()));

        Map<UUID, AttendeeRole> desiredRoles = new LinkedHashMap<>();
        for (UUID uid : newParticipantSet) {
            desiredRoles.put(uid, uid.equals(booking.getOrganizer().getId()) ? AttendeeRole.ORGANIZER : AttendeeRole.REQUIRED);
        }

        Booking savedBooking = bookingRepository.saveAndFlush(booking);
        Set<UUID> newlyAddedIds = new LinkedHashSet<>();
        syncAttendeesDiff(savedBooking, existingByUserId, desiredRoles, usersById, timeChanged, newlyAddedIds);

        change.setAssignedRoom(targetRoom);
        change.setStatus(BookingChangeStatus.APPROVED);
        change.setProcessedBy(admin.id());
        change.setProcessedAt(now);
        change.setProcessReason(rq.note() != null ? rq.note().trim() : null);
        BookingChange savedChange = bookingChangeRepository.saveAndFlush(change);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(savedBooking.getId())
                .actorId(admin.id())
                .action("APPROVE_CHANGE")
                .reason(change.getProcessReason())
                .details(Map.of(
                        "changeId", savedChange.getId().toString(),
                        "assignedRoomId", targetRoom.getId().toString(),
                        "newStartTime", savedBooking.getStartTime().toString(),
                        "newEndTime", savedBooking.getEndTime().toString()
                ))
                .build());

        notificationService.sendBulk(
                newParticipantSet,
                savedBooking.getId(),
                "BOOKING_CHANGE_APPROVED",
                "Đề nghị thay đổi lịch họp đã được phê duyệt",
                String.format("Lịch họp đã được cập nhật sang phòng %s (%s - %s).",
                        targetRoom.getName(), savedBooking.getStartTime(), savedBooking.getEndTime()),
                Map.of("roomId", targetRoom.getId().toString(), "roomName", targetRoom.getName()),
                "CHANGE_APPROVE:" + savedChange.getId()
        );

        return BookingChangeResponse.from(savedChange, admin);
    }

    /**
     * Admin từ chối đề nghị thay đổi lịch đã duyệt (Spec 9 & AC-15):
     * - Lịch gốc giữ nguyên phòng và giờ cũ.
     */
    @Transactional
    public BookingChangeResponse rejectBookingChange(AuthUser admin, UUID changeId, RejectBookingRequest rq) {
        BookingChange change = bookingChangeRepository.findWithDetailsById(changeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đề nghị thay đổi lịch"));

        Booking booking = change.getBooking();
        if (booking.getOrganizer().getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if (rq.expectedVersion() != null && change.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (change.getStatus() != BookingChangeStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể từ chối đề nghị đang ở trạng thái PENDING");
        }

        String reason = rq.reason().trim();
        change.setStatus(BookingChangeStatus.REJECTED);
        change.setProcessedBy(admin.id());
        change.setProcessedAt(OffsetDateTime.now());
        change.setProcessReason(reason);

        BookingChange saved = bookingChangeRepository.saveAndFlush(change);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(booking.getId())
                .actorId(admin.id())
                .action("REJECT_CHANGE")
                .reason(reason)
                .details(Map.of("changeId", saved.getId().toString()))
                .build());

        notificationService.send(
                booking.getOrganizer().getId(),
                booking.getId(),
                "BOOKING_CHANGE_REJECTED",
                "Đề nghị thay đổi lịch họp bị từ chối",
                "Đề nghị thay đổi lịch họp của bạn đã bị từ chối (lịch gốc vẫn giữ nguyên). Lý do: " + reason,
                Map.of("changeId", saved.getId().toString(), "reason", reason),
                "CHANGE_REJECT:" + saved.getId()
        );

        return BookingChangeResponse.from(saved, admin);
    }

    /**
     * Chủ lịch rút lại đề nghị thay đổi đang PENDING trước khi Admin xử lý (Spec 9 & 23.2: POST /booking-changes/{id}/withdraw).
     */
    @Transactional
    public BookingChangeResponse withdrawBookingChange(AuthUser actor, UUID changeId, CancelBookingRequest rq) {
        BookingChange change = bookingChangeRepository.findWithDetailsById(changeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đề nghị thay đổi lịch"));

        Booking booking = change.getBooking();
        if (!booking.getOrganizer().getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người tổ chức mới được rút đề nghị thay đổi của mình");
        }
        if (rq != null && rq.expectedVersion() != null && change.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (change.getStatus() != BookingChangeStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATE, "Chỉ có thể rút đề nghị đang ở trạng thái PENDING");
        }

        String reason = rq != null && rq.reason() != null && !rq.reason().isBlank()
                ? rq.reason().trim()
                : "Người tổ chức chủ động rút đề nghị thay đổi";

        change.setStatus(BookingChangeStatus.CANCELLED);
        change.setProcessedBy(actor.id());
        change.setProcessedAt(OffsetDateTime.now());
        change.setProcessReason(reason);

        BookingChange saved = bookingChangeRepository.saveAndFlush(change);

        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(booking.getId())
                .actorId(actor.id())
                .action("WITHDRAW_CHANGE")
                .reason(reason)
                .details(Map.of("changeId", saved.getId().toString()))
                .build());

        notifyActiveAdminsExcept(
                actor.id(),
                booking,
                "BOOKING_CHANGE_WITHDRAWN",
                "Đề nghị thay đổi lịch họp đã được rút lại",
                "Người tổ chức đã rút lại đề nghị thay đổi lịch họp.",
                "CHANGE_WITHDRAW:" + saved.getId()
        );

        return BookingChangeResponse.from(saved, actor);
    }

    // ----- Helpers -----

    public static OffsetDateTime computeCheckInDeadline(OffsetDateTime startTime, OffsetDateTime endTime) {
        OffsetDateTime plusTen = startTime.plus(CHECK_IN_GRACE_WINDOW);
        return plusTen.isBefore(endTime) ? plusTen : endTime;
    }

    private static Visibility resolveAndValidateVisibility(
            MeetingType meetingType,
            Visibility requestedVisibility,
            Boolean legacyIsConfidential,
            Visibility currentVisibility) {
        Visibility effective = requestedVisibility;
        if (effective == null && Boolean.TRUE.equals(legacyIsConfidential)) {
            effective = Visibility.CONFIDENTIAL;
        }

        // Hồ sơ đã CONFIDENTIAL không được hạ về STANDARD (Spec 27.4 & AC-72)
        if (currentVisibility == Visibility.CONFIDENTIAL && effective == Visibility.STANDARD) {
            throw new BusinessException(ErrorCode.VISIBILITY_NOT_ALLOWED, "Hồ sơ đã ở mức CONFIDENTIAL không được phép hạ về STANDARD");
        }

        // Các loại cuộc họp nhạy cảm (SALARY_REVIEW, PERFORMANCE_REVIEW, INTERVIEW) bắt buộc CONFIDENTIAL (POL-23, AC-72)
        if (meetingType.isMandatoryConfidential()) {
            if (effective == Visibility.STANDARD) {
                throw new BusinessException(ErrorCode.VISIBILITY_NOT_ALLOWED);
            }
            return Visibility.CONFIDENTIAL;
        }

        if (currentVisibility == Visibility.CONFIDENTIAL) {
            return Visibility.CONFIDENTIAL;
        }
        return effective != null ? effective : Visibility.STANDARD;
    }

    private Team validateAndLoadOrganizerTeam(UUID organizerId, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        Team team = teamService.findOrThrow(teamId);
        if (!userTeamRepository.existsById(new UserTeamId(organizerId, teamId))) {
            throw new BusinessException(ErrorCode.TEAM_MEMBERSHIP_REQUIRED);
        }
        return team;
    }

    private WorkGroup validateAndLoadOrganizerGroup(UUID organizerId, UUID groupId) {
        if (groupId == null) {
            return null;
        }
        WorkGroup group = groupService.findOrThrow(groupId);
        if (!userGroupRepository.existsById(new UserGroupId(organizerId, groupId))) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBERSHIP_REQUIRED);
        }
        return group;
    }

    private void notifyActiveAdminsExcept(
            UUID excludeUserId,
            Booking booking,
            String type,
            String title,
            String message,
            String dedupePrefix) {
        Set<UUID> adminIds = userRepo.findActiveAdminIds().stream()
                .filter(id -> !id.equals(excludeUserId))
                .collect(Collectors.toSet());
        notificationService.sendBulk(
                adminIds,
                booking.getId(),
                type,
                title,
                message,
                Map.of("bookingId", booking.getId().toString(), "visibility", booking.getVisibility().name()),
                dedupePrefix
        );
    }

    private Map<UUID, AttendeeRole> resolveParticipantRoles(
            UUID organizerId,
            Set<UUID> requiredIds,
            Set<UUID> optionalIds,
            UUID teamId,
            UUID groupId,
            boolean inviteAllScopeMembers) {
        Map<UUID, AttendeeRole> roles = new LinkedHashMap<>();

        if (optionalIds != null) {
            optionalIds.stream().filter(Objects::nonNull)
                    .forEach(id -> roles.put(id, AttendeeRole.OPTIONAL));
        }

        if (inviteAllScopeMembers) {
            if (teamId != null) {
                List<UUID> teamUserIds = userTeamRepository.findActiveUserIdsByTeamIds(List.of(teamId));
                teamUserIds.forEach(id -> roles.put(id, AttendeeRole.REQUIRED));
            }
            if (groupId != null) {
                List<UUID> groupUserIds = userGroupRepository.findActiveUserIdsByGroupIds(List.of(groupId));
                groupUserIds.forEach(id -> roles.put(id, AttendeeRole.REQUIRED));
            }
        }

        if (requiredIds != null) {
            requiredIds.stream().filter(Objects::nonNull)
                    .forEach(id -> roles.put(id, AttendeeRole.REQUIRED));
        }

        roles.put(organizerId, AttendeeRole.ORGANIZER);
        return roles;
    }

    private Map<UUID, User> lockAndLoadActiveUsersOrThrow(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> sortedIds = userIds.stream().sorted().toList();
        List<User> users = userRepo.findAllByIdInOrderByIdForUpdate(sortedIds).stream()
                .filter(u -> u.isActive() && !u.isDeleted())
                .toList();
        if (users.size() != sortedIds.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Một hoặc nhiều thành viên được mời không tồn tại hoặc đã ngừng hoạt động");
        }
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<BookingAttendee> saveAttendees(
            Booking booking,
            Map<UUID, AttendeeRole> participantRoles,
            Map<UUID, User> usersById) {
        OffsetDateTime now = OffsetDateTime.now();
        List<BookingAttendee> entities = new ArrayList<>(participantRoles.size());
        for (var entry : participantRoles.entrySet()) {
            UUID userId = entry.getKey();
            AttendeeRole role = entry.getValue();
            boolean isOrganizer = role == AttendeeRole.ORGANIZER;
            entities.add(BookingAttendee.builder()
                    .id(new BookingAttendeeId(booking.getId(), userId))
                    .booking(booking)
                    .user(usersById.get(userId))
                    .attendeeRole(role)
                    .rsvpStatus(isOrganizer ? RsvpStatus.ACCEPTED : RsvpStatus.PENDING)
                    .respondedAt(isOrganizer ? now : null)
                    .build());
        }
        return attendeeRepository.saveAllAndFlush(entities);
    }

    private List<BookingAttendee> syncAttendeesDiff(
            Booking booking,
            Map<UUID, BookingAttendee> existingByUserId,
            Map<UUID, AttendeeRole> desiredRoles,
            Map<UUID, User> usersById,
            boolean resetRsvpDueToTimeChange,
            Set<UUID> newlyAddedUserIdsOut) {
        OffsetDateTime now = OffsetDateTime.now();

        List<BookingAttendee> toDelete = existingByUserId.values().stream()
                .filter(a -> !desiredRoles.containsKey(a.getUser().getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            // Thông báo tối thiểu cho người bị loại khỏi danh sách tham dự (Spec 12.2 & AC-40)
            Set<UUID> removedUserIds = toDelete.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
            notificationService.sendBulk(
                    removedUserIds,
                    booking.getId(),
                    "BOOKING_PARTICIPANT_REMOVED",
                    "Cập nhật danh sách tham dự cuộc họp",
                    "Bạn không còn nằm trong danh sách tham dự của cuộc họp này.",
                    Map.of("bookingId", booking.getId().toString()),
                    null
            );
            attendeeRepository.deleteAll(toDelete);
            attendeeRepository.flush();
        }

        List<BookingAttendee> toSave = new ArrayList<>(desiredRoles.size());
        for (var entry : desiredRoles.entrySet()) {
            UUID userId = entry.getKey();
            AttendeeRole role = entry.getValue();
            boolean isOrganizer = role == AttendeeRole.ORGANIZER;
            BookingAttendee existing = existingByUserId.get(userId);

            if (existing != null) {
                existing.setAttendeeRole(role);
                if (isOrganizer) {
                    existing.setRsvpStatus(RsvpStatus.ACCEPTED);
                    existing.setDeclineReason(null);
                    if (existing.getRespondedAt() == null) {
                        existing.setRespondedAt(now);
                    }
                } else if (resetRsvpDueToTimeChange) {
                    existing.setRsvpStatus(RsvpStatus.PENDING);
                    existing.setDeclineReason(null);
                    existing.setRespondedAt(null);
                }
                toSave.add(existing);
            } else {
                newlyAddedUserIdsOut.add(userId);
                toSave.add(BookingAttendee.builder()
                        .id(new BookingAttendeeId(booking.getId(), userId))
                        .booking(booking)
                        .user(usersById.get(userId))
                        .attendeeRole(role)
                        .rsvpStatus(isOrganizer ? RsvpStatus.ACCEPTED : RsvpStatus.PENDING)
                        .respondedAt(isOrganizer ? now : null)
                        .build());
            }
        }
        return attendeeRepository.saveAllAndFlush(toSave);
    }

    private PageResponse<BookingResponse> toPageResponse(Page<Booking> bookingsPage, AuthUser viewer) {
        Map<UUID, List<BookingAttendee>> attendeesByBooking = loadAttendeesMap(bookingsPage.getContent());
        return PageResponse.from(
                bookingsPage.map(b -> BookingResponse.from(b, attendeesByBooking.getOrDefault(b.getId(), List.of()), viewer))
        );
    }

    private Map<UUID, List<BookingAttendee>> loadAttendeesMap(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = bookings.stream().map(Booking::getId).toList();
        return attendeeRepository.findById_BookingIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getBooking().getId()));
    }

    private Booking findOrThrow(UUID id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private static void assertExpectedVersion(Booking booking, Long expectedVersion) {
        if (expectedVersion != null && booking.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * Kiểm tra khung thời gian theo đúng Spec mục 7.2 & POL-05:
     * - startAt < endAt và ở tương lai.
     * - Độ chính xác đến phút (từ chối giây / nano khác 0).
     * - Nằm trong một ngày địa phương (Asia/Ho_Chi_Minh), cho phép kết thúc đúng 00:00 ngày kế tiếp.
     */
    private static void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (startTime.getSecond() != 0 || startTime.getNano() != 0 || endTime.getSecond() != 0 || endTime.getNano() != 0) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời gian bắt đầu và kết thúc phải có độ chính xác đến phút (giây và phần giây phải bằng 0)");
        }
        if (!startTime.isAfter(OffsetDateTime.now().minusMinutes(1))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời gian bắt đầu phải ở trong tương lai");
        }

        ZonedDateTime localStart = startTime.atZoneSameInstant(BUSINESS_ZONE);
        ZonedDateTime localEnd = endTime.atZoneSameInstant(BUSINESS_ZONE);
        LocalDate startDate = localStart.toLocalDate();
        LocalDate endDate = localEnd.toLocalDate();

        boolean sameDay = startDate.equals(endDate);
        boolean endsAtMidnightNextDay = endDate.equals(startDate.plusDays(1)) && localEnd.toLocalTime().equals(LocalTime.MIDNIGHT);
        if (!sameDay && !endsAtMidnightNextDay) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian họp phải nằm trong cùng một ngày địa phương (Asia/Ho_Chi_Minh) hoặc kết thúc đúng 00:00 ngày kế tiếp");
        }
    }

    private static void validateRoomForBooking(Room room, Integer attendeeCount) {
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_AVAILABLE_STATUS);
        }
        if (attendeeCount != null && attendeeCount > room.getCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_CAPACITY_EXCEEDED);
        }
    }

    private static void assertOrganizerOrManager(AuthUser actor, Booking booking) {
        boolean isOrganizer = booking.getOrganizer().getId().equals(actor.id());
        boolean canManageAll = "ADMIN".equalsIgnoreCase(actor.role()) || actor.hasAuthority("BOOKING_MANAGE");
        if (!isOrganizer && !canManageAll) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
