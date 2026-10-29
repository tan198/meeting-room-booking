package com.tanle.meeting_room_booking.room.repository;

import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("""
            SELECT r FROM Room r
            WHERE (:keyword = '' OR LOWER(r.name) LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
              AND (:floor IS NULL OR r.floor = :floor)
              AND (:minCapacity IS NULL OR r.capacity >= :minCapacity)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<Room> searchRooms(
            @Param("keyword") String keyword,
            @Param("floor") Integer floor,
            @Param("minCapacity") Integer minCapacity,
            @Param("status") RoomStatus status,
            Pageable pageable
    );

    /**
     * Tìm các phòng đang ACTIVE, đủ sức chứa và không có lịch đặt phòng (CONFIRMED hoặc IN_PROGRESS)
     * bị giao thoa với khoảng thời gian [startTime, endTime).
     */
    @Query("""
            SELECT r FROM Room r
            WHERE r.status = com.tanle.meeting_room_booking.room.entity.RoomStatus.ACTIVE
              AND (:floor IS NULL OR r.floor = :floor)
              AND (:minCapacity IS NULL OR r.capacity >= :minCapacity)
              AND NOT EXISTS (
                  SELECT 1 FROM Booking b
                  WHERE b.room.id = r.id
                    AND b.status IN (
                        com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                        com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                        com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
                    )
                    AND b.usageStatus NOT IN (
                        com.tanle.meeting_room_booking.booking.entity.UsageStatus.NO_SHOW,
                        com.tanle.meeting_room_booking.booking.entity.UsageStatus.INTERRUPTED
                    )
                    AND b.startTime < :endTime
                    AND COALESCE(b.releasedAt, b.endTime) > :startTime
              )
            ORDER BY r.capacity ASC, r.floor ASC, r.name ASC
            """)
    List<Room> findAvailableRooms(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("minCapacity") Integer minCapacity,
            @Param("floor") Integer floor
    );
}
