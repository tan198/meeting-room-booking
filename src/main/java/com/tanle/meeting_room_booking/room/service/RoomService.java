package com.tanle.meeting_room_booking.room.service;

import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.room.dto.CreateRoomRequest;
import com.tanle.meeting_room_booking.room.dto.RoomResponse;
import com.tanle.meeting_room_booking.room.dto.UpdateRoomRequest;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest rq) {
        String name = rq.name().trim();
        if (roomRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phòng họp '" + name + "' đã tồn tại");
        }

        Room room = Room.builder()
                .name(name)
                .floor(rq.floor())
                .capacity(rq.capacity())
                .facilities(normalizeFacilities(rq.facilities()))
                .status(RoomStatus.ACTIVE)
                .build();

        return RoomResponse.from(roomRepository.saveAndFlush(room));
    }

    public RoomResponse getById(UUID id) {
        return RoomResponse.from(findOrThrow(id));
    }

    public PageResponse<RoomResponse> search(String keyword, Integer floor, Integer minCapacity, RoomStatus status, int page, int size) {
        String kw = (keyword == null || keyword.isBlank())
                ? ""
                : escapeLike(keyword.trim().toLowerCase(Locale.ROOT));
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.asc("floor"), Sort.Order.asc("name"))
        );
        return PageResponse.from(roomRepository.searchRooms(kw, floor, minCapacity, status, pageable).map(RoomResponse::from));
    }

    public List<RoomResponse> findAvailableRooms(OffsetDateTime startTime, OffsetDateTime endTime, Integer minCapacity, Integer floor) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "Thời gian bắt đầu phải nhỏ hơn thời gian kết thúc");
        }
        return roomRepository.findAvailableRooms(startTime, endTime, minCapacity, floor)
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest rq) {
        Room room = findForUpdateOrThrow(id);
        String name = rq.name().trim();

        if (roomRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phòng họp '" + name + "' đã tồn tại");
        }

        // Theo Spec 6.1 & AC-26: Chặn thao tác chuyển INACTIVE hoặc giảm sức chứa nếu làm bất kỳ lịch đang giữ phòng hiệu lực từ hiện tại trở đi không còn hợp lệ
        OffsetDateTime now = OffsetDateTime.now();
        if (rq.status() != RoomStatus.ACTIVE) {
            var affected = bookingRepository.findFutureBookingsViolatingRoomChange(id, now, null);
            if (!affected.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.INVALID_BOOKING_STATE,
                        "Không thể ngừng hoạt động phòng khi vẫn còn " + affected.size() + " lịch họp đang giữ hiệu lực. Vui lòng chuyển phòng hoặc hủy các lịch bị ảnh hưởng trước.",
                        java.util.Map.of("affectedBookingIds", affected.stream().map(b -> b.getId().toString()).toList())
                );
            }
        } else if (rq.capacity() < room.getCapacity()) {
            var capViolations = bookingRepository.findFutureBookingsViolatingRoomChange(id, now, rq.capacity());
            if (!capViolations.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.ROOM_REQUIREMENTS_NOT_MET,
                        "Không thể giảm sức chứa xuống " + rq.capacity() + " vì có " + capViolations.size() + " lịch họp tương lai vượt quá sức chứa mới.",
                        java.util.Map.of("affectedBookingIds", capViolations.stream().map(b -> b.getId().toString()).toList())
                );
            }
        }

        room.setName(name);
        room.setFloor(rq.floor());
        room.setCapacity(rq.capacity());
        room.setFacilities(normalizeFacilities(rq.facilities()));
        room.setStatus(rq.status());

        return RoomResponse.from(roomRepository.saveAndFlush(room));
    }

    @Transactional
    public void deleteRoom(UUID id) {
        Room room = findForUpdateOrThrow(id);
        if (bookingRepository.existsActiveBookingsForRoom(id, OffsetDateTime.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể xóa phòng họp đang có lịch đặt hoạt động (CONFIRMED / IN_PROGRESS). Vui lòng hủy hoặc chuyển phòng cho các lịch họp trước."
            );
        }
        room.setDeletedAt(OffsetDateTime.now());
        room.setStatus(RoomStatus.INACTIVE);
    }

    public Room findOrThrow(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
    }

    public Room findForUpdateOrThrow(UUID id) {
        return roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
    }

    private static List<String> normalizeFacilities(List<String> facilities) {
        if (facilities == null || facilities.isEmpty()) {
            return List.of();
        }
        return facilities.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
