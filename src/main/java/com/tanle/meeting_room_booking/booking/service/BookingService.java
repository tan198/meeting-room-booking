package com.tanle.meeting_room_booking.booking.service;

import com.tanle.meeting_room_booking.booking.dto.BookingResponse;
import com.tanle.meeting_room_booking.booking.dto.CancelBookingRequest;
import com.tanle.meeting_room_booking.booking.dto.CreateBookingRequest;
import com.tanle.meeting_room_booking.booking.dto.UpdateBookingRequest;
import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.entity.BookingStatus;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.service.RoomService;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Duration EARLY_CHECK_IN_WINDOW = Duration.ofMinutes(15);
    private static final OffsetDateTime MIN_BOUND = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_BOUND = OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private final BookingRepository bookingRepository;
    private final RoomService roomService;
    private final UserRepo userRepo;

    @Transactional
    public BookingResponse createBooking(UUID organizerId, CreateBookingRequest rq) {
        validateTimeRange(rq.startTime(), rq.endTime());

        Room room = roomService.findOrThrow(rq.roomId());
        validateRoomForBooking(room, rq.attendeeCount());

        if (bookingRepository.existsOverlappingBooking(room.getId(), rq.startTime(), rq.endTime(), null)) {
            throw new BusinessException(ErrorCode.ROOM_TIME_CONFLICT);
        }

        User organizer = userRepo.findById(organizerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Booking booking = Booking.builder()
                .room(room)
                .organizer(organizer)
                .title(rq.title().trim())
                .description(rq.description() != null ? rq.description().trim() : null)
                .startTime(rq.startTime())
                .endTime(rq.endTime())
                .status(BookingStatus.CONFIRMED)
                .build();

        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    public BookingResponse getById(UUID id) {
        return BookingResponse.from(findOrThrow(id));
    }

    public PageResponse<BookingResponse> search(
            UUID roomId,
            UUID organizerId,
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
        return PageResponse.from(
                bookingRepository.searchBookings(roomId, organizerId, status, effectiveFrom, effectiveTo, pageable)
                        .map(BookingResponse::from)
        );
    }

    public List<BookingResponse> getRoomSchedule(UUID roomId, OffsetDateTime fromTime, OffsetDateTime toTime) {
        if (fromTime == null || toTime == null || !fromTime.isBefore(toTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Khoảng thời gian truy vấn lịch không hợp lệ");
        }
        roomService.findOrThrow(roomId);
        return bookingRepository.findRoomSchedule(roomId, fromTime, toTime)
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse updateBooking(AuthUser actor, UUID bookingId, UpdateBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);
        assertOrganizerOrAdmin(actor, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ có thể chỉnh sửa lịch đặt phòng khi đang ở trạng thái CONFIRMED"
            );
        }

        validateTimeRange(rq.startTime(), rq.endTime());

        Room targetRoom = (rq.roomId() != null && !rq.roomId().equals(booking.getRoom().getId()))
                ? roomService.findOrThrow(rq.roomId())
                : booking.getRoom();

        validateRoomForBooking(targetRoom, rq.attendeeCount());

        if (bookingRepository.existsOverlappingBooking(targetRoom.getId(), rq.startTime(), rq.endTime(), booking.getId())) {
            throw new BusinessException(ErrorCode.ROOM_TIME_CONFLICT);
        }

        booking.setRoom(targetRoom);
        booking.setTitle(rq.title().trim());
        booking.setDescription(rq.description() != null ? rq.description().trim() : null);
        booking.setStartTime(rq.startTime());
        booking.setEndTime(rq.endTime());

        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    @Transactional
    public BookingResponse cancelBooking(AuthUser actor, UUID bookingId, CancelBookingRequest rq) {
        Booking booking = findOrThrow(bookingId);
        assertOrganizerOrAdmin(actor, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể hủy lịch đặt phòng ở trạng thái " + booking.getStatus()
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledBy(actor.id());
        booking.setCancelledAt(OffsetDateTime.now());
        booking.setCancelReason(rq != null && rq.reason() != null && !rq.reason().isBlank() ? rq.reason().trim() : null);

        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    @Transactional
    public BookingResponse checkIn(AuthUser actor, UUID bookingId) {
        Booking booking = findOrThrow(bookingId);
        assertOrganizerOrAdmin(actor, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ có thể check-in lịch đặt phòng ở trạng thái CONFIRMED"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(booking.getStartTime().minus(EARLY_CHECK_IN_WINDOW))) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ được phép check-in trước giờ bắt đầu tối đa 15 phút"
            );
        }
        if (!now.isBefore(booking.getEndTime())) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Khung giờ đặt phòng đã kết thúc, không thể check-in"
            );
        }

        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setActualStartTime(now);

        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    @Transactional
    public BookingResponse checkOut(AuthUser actor, UUID bookingId) {
        Booking booking = findOrThrow(bookingId);
        assertOrganizerOrAdmin(actor, booking);

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ có thể check-out khi cuộc họp đang diễn ra (IN_PROGRESS)"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime actualEnd = now.isBefore(booking.getActualStartTime()) ? booking.getActualStartTime() : now;

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualEndTime(actualEnd);
        if (actualEnd.isAfter(booking.getStartTime()) && actualEnd.isBefore(booking.getEndTime())) {
            booking.setEndTime(actualEnd);
        }

        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    private Booking findOrThrow(UUID id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private static void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (startTime.isBefore(OffsetDateTime.now().minusMinutes(1))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
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

    private static void assertOrganizerOrAdmin(AuthUser actor, Booking booking) {
        boolean isOrganizer = booking.getOrganizer().getId().equals(actor.id());
        boolean canManageAll = actor.hasAuthority("BOOKING_MANAGE");
        if (!isOrganizer && !canManageAll) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
