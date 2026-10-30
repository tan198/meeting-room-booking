package com.tanle.meeting_room_booking.room.service;

import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.room.dto.CreateEquipmentRequest;
import com.tanle.meeting_room_booking.room.dto.EquipmentResponse;
import com.tanle.meeting_room_booking.room.dto.RoomResponse;
import com.tanle.meeting_room_booking.room.dto.UpdateEquipmentRequest;
import com.tanle.meeting_room_booking.room.entity.Equipment;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.repository.EquipmentRepository;
import com.tanle.meeting_room_booking.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final BookingRepository bookingRepository;

    @Transactional
    public EquipmentResponse createEquipment(CreateEquipmentRequest rq) {
        String code = rq.code().trim().toUpperCase(Locale.ROOT);
        if (equipmentRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Mã thiết bị '" + code + "' đã tồn tại");
        }

        Equipment eq = Equipment.builder()
                .code(code)
                .name(rq.name().trim())
                .description(rq.description() != null ? rq.description().trim() : null)
                .status(RoomStatus.ACTIVE)
                .build();

        return EquipmentResponse.from(equipmentRepository.saveAndFlush(eq));
    }

    @Transactional
    public EquipmentResponse updateEquipment(UUID id, UpdateEquipmentRequest rq) {
        Equipment eq = findOrThrow(id);

        if (rq.expectedVersion() != null && eq.getVersion() != rq.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        if (rq.status() == RoomStatus.INACTIVE && eq.getStatus() == RoomStatus.ACTIVE) {
            // Theo Spec 21.4 & AC-39: Ngừng loại thiết bị bị chặn nếu còn phòng ACTIVE đang gắn thiết bị đó và có lịch tương lai
            String codeUpper = eq.getCode().toUpperCase(Locale.ROOT);
            String nameUpper = eq.getName().toUpperCase(Locale.ROOT);
            OffsetDateTime now = OffsetDateTime.now();

            List<Room> dependentRooms = roomRepository.findAll().stream()
                    .filter(r -> r.getStatus() == RoomStatus.ACTIVE && r.getFacilities() != null)
                    .filter(r -> r.getFacilities().stream()
                            .filter(Objects::nonNull)
                            .map(s -> s.trim().toUpperCase(Locale.ROOT))
                            .anyMatch(f -> f.equals(codeUpper) || f.equals(nameUpper)))
                    .toList();

            List<String> affectedBookingIds = new ArrayList<>();
            for (Room r : dependentRooms) {
                List<Booking> futureBookings = bookingRepository.findFutureBookingsViolatingRoomChange(r.getId(), now, null);
                for (Booking b : futureBookings) {
                    affectedBookingIds.add(b.getId().toString());
                }
            }
            if (!affectedBookingIds.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.ACTIVE_DEPENDENCIES_EXIST,
                        "Không thể ngừng thiết bị '" + eq.getCode() + "' vì đang có " + affectedBookingIds.size() + " lịch họp tương lai tại các phòng sử dụng thiết bị này",
                        Map.of("affectedBookingIds", affectedBookingIds)
                );
            }
        }

        if (rq.name() != null && !rq.name().isBlank()) {
            eq.setName(rq.name().trim());
        }
        if (rq.description() != null) {
            eq.setDescription(rq.description().trim());
        }
        if (rq.status() != null) {
            eq.setStatus(rq.status());
        }

        return EquipmentResponse.from(equipmentRepository.saveAndFlush(eq));
    }

    @Transactional
    public RoomResponse updateRoomEquipment(UUID roomId, List<String> equipments) {
        Room room = roomService.findForUpdateOrThrow(roomId);
        List<String> normalized = equipments == null
                ? List.of()
                : equipments.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();

        room.setFacilities(new ArrayList<>(normalized));
        return RoomResponse.from(roomRepository.saveAndFlush(room));
    }

    public EquipmentResponse getById(UUID id) {
        return EquipmentResponse.from(findOrThrow(id));
    }

    public List<EquipmentResponse> findAll(RoomStatus status) {
        List<Equipment> list = status != null
                ? equipmentRepository.findByStatusOrderByCodeAsc(status)
                : equipmentRepository.findAllByOrderByCodeAsc();
        return list.stream().map(EquipmentResponse::from).toList();
    }

    public Equipment findOrThrow(UUID id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND));
    }
}

