package com.tanle.meeting_room_booking.booking.repository;

import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"room", "organizer"})
    Optional<Booking> findWithDetailsById(UUID id);

    /**
     * Kiểm tra trùng khung giờ [startTime, endTime) đối với các booking đang chiếm phòng (CONFIRMED, IN_PROGRESS).
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND b.startTime < :endTime
              AND b.endTime > :startTime
              AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") UUID roomId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("excludeId") UUID excludeId
    );

    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.room r
            JOIN FETCH b.organizer o
            WHERE (:roomId IS NULL OR r.id = :roomId)
              AND (:organizerId IS NULL OR o.id = :organizerId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """,
            countQuery = """
            SELECT COUNT(b) FROM Booking b
            WHERE (:roomId IS NULL OR b.room.id = :roomId)
              AND (:organizerId IS NULL OR b.organizer.id = :organizerId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """)
    Page<Booking> searchBookings(
            @Param("roomId") UUID roomId,
            @Param("organizerId") UUID organizerId,
            @Param("status") BookingStatus status,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime,
            Pageable pageable
    );

    /**
     * Xem lịch đặt theo phòng trong khoảng [fromTime, toTime) sắp xếp theo giờ bắt đầu tăng dần.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.room r
            JOIN FETCH b.organizer o
            WHERE r.id = :roomId
              AND b.endTime > :fromTime
              AND b.startTime < :toTime
            ORDER BY b.startTime ASC
            """)
    List<Booking> findRoomSchedule(
            @Param("roomId") UUID roomId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    // ----- Scheduler queries (tận dụng partial indexes trong V1__init_schema.sql) -----

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking b
            SET b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.NO_SHOW,
                b.updatedAt = :now,
                b.version = b.version + 1
            WHERE b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED
              AND b.startTime <= :cutoff
            """)
    int markNoShowBookings(@Param("cutoff") OffsetDateTime cutoff, @Param("now") OffsetDateTime now);

    @EntityGraph(attributePaths = {"room", "organizer"})
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              AND b.preEndNotified = false
              AND b.endTime > :now
              AND b.endTime <= :notifyWindowEnd
            """)
    List<Booking> findInProgressNearEnd(
            @Param("now") OffsetDateTime now,
            @Param("notifyWindowEnd") OffsetDateTime notifyWindowEnd
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking b
            SET b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.COMPLETED,
                b.actualEndTime = COALESCE(b.actualEndTime, :now),
                b.updatedAt = :now,
                b.version = b.version + 1
            WHERE b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              AND b.endTime <= :now
            """)
    int completeExpiredInProgressBookings(@Param("now") OffsetDateTime now);
}
