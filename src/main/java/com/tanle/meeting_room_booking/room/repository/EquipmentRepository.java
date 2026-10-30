package com.tanle.meeting_room_booking.room.repository;

import com.tanle.meeting_room_booking.room.entity.Equipment;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    Optional<Equipment> findByCodeIgnoreCase(String code);

    List<Equipment> findByStatusOrderByCodeAsc(RoomStatus status);

    List<Equipment> findAllByOrderByCodeAsc();
}

