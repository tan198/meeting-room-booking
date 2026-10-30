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

    @EntityGraph(attributePaths = {"room", "preferredRoom", "organizer", "team", "group"})
    Optional<Booking> findWithDetailsById(UUID id);

    /**
     * Kiểm tra trùng khung giờ [startTime, endTime) đối với các booking đã được duyệt/xác nhận và đang giữ phòng hiệu lực.
     * Tính cả trường hợp đã giải phóng sớm (releasedAt < endTime) theo Spec 25.5.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
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
              AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") UUID roomId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Kiểm tra phòng có đang có cuộc họp khác ở trạng thái đang sử dụng (IN_USE / IN_PROGRESS) hay không.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
              AND (b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.IN_USE
                   OR b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS)
              AND b.id <> :excludeId
            """)
    boolean existsInProgressBookingInRoom(
            @Param("roomId") UUID roomId,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Kiểm tra phòng có lịch đặt đang giữ hiệu lực từ hiện tại trở đi trước khi ngừng sử dụng / xóa mềm phòng (Spec 6.1).
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND COALESCE(b.releasedAt, b.endTime) > :now
            """)
    boolean existsActiveBookingsForRoom(@Param("roomId") UUID roomId, @Param("now") OffsetDateTime now);

    /**
     * Kiểm tra người dùng có đang là Organizer của lịch họp đang hoạt động trước khi xóa mềm hoặc vô hiệu hóa user.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.organizer.id = :organizerId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.PENDING,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND COALESCE(b.releasedAt, b.endTime) > :now
            """)
    boolean existsActiveBookingsByOrganizer(@Param("organizerId") UUID organizerId, @Param("now") OffsetDateTime now);

    @Query(value = """
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH b.preferredRoom pr
            JOIN FETCH b.organizer o
            LEFT JOIN FETCH b.team t
            LEFT JOIN FETCH b.group g
            WHERE (:roomId IS NULL OR r.id = :roomId OR pr.id = :roomId)
              AND (:organizerId IS NULL OR o.id = :organizerId)
              AND (:teamId IS NULL OR t.id = :teamId)
              AND (:groupId IS NULL OR g.id = :groupId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """,
            countQuery = """
            SELECT COUNT(b) FROM Booking b
            LEFT JOIN b.room r
            LEFT JOIN b.preferredRoom pr
            WHERE (:roomId IS NULL OR r.id = :roomId OR pr.id = :roomId)
              AND (:organizerId IS NULL OR b.organizer.id = :organizerId)
              AND (:teamId IS NULL OR b.team.id = :teamId)
              AND (:groupId IS NULL OR b.group.id = :groupId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """)
    Page<Booking> searchBookings(
            @Param("roomId") UUID roomId,
            @Param("organizerId") UUID organizerId,
            @Param("teamId") UUID teamId,
            @Param("groupId") UUID groupId,
            @Param("status") BookingStatus status,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime,
            Pageable pageable
    );

    /**
     * "Lịch của tôi": chỉ cần 1 truy vấn qua bảng booking_attendees (vì cả ORGANIZER cũng là 1 dòng trong booking_attendees).
     */
    @Query(value = """
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH b.preferredRoom pr
            JOIN FETCH b.organizer o
            LEFT JOIN FETCH b.team t
            LEFT JOIN FETCH b.group g
            WHERE EXISTS (
                SELECT 1 FROM BookingAttendee ba
                WHERE ba.booking.id = b.id AND ba.user.id = :userId
            )
              AND (:teamId IS NULL OR t.id = :teamId)
              AND (:groupId IS NULL OR g.id = :groupId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """,
            countQuery = """
            SELECT COUNT(b) FROM Booking b
            WHERE EXISTS (
                SELECT 1 FROM BookingAttendee ba
                WHERE ba.booking.id = b.id AND ba.user.id = :userId
            )
              AND (:teamId IS NULL OR b.team.id = :teamId)
              AND (:groupId IS NULL OR b.group.id = :groupId)
              AND (:status IS NULL OR b.status = :status)
              AND (b.endTime > :fromTime)
              AND (b.startTime < :toTime)
            """)
    Page<Booking> findMySchedule(
            @Param("userId") UUID userId,
            @Param("teamId") UUID teamId,
            @Param("groupId") UUID groupId,
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
            LEFT JOIN FETCH b.preferredRoom pr
            JOIN FETCH b.organizer o
            LEFT JOIN FETCH b.team t
            LEFT JOIN FETCH b.group g
            WHERE r.id = :roomId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.COMPLETED
              )
              AND COALESCE(b.releasedAt, b.endTime) > :fromTime
              AND b.startTime < :toTime
            ORDER BY b.startTime ASC
            """)
    List<Booking> findRoomSchedule(
            @Param("roomId") UUID roomId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    /**
     * Tìm các booking đã được duyệt (APPROVED / CONFIRMED / IN_PROGRESS) còn giữ phòng hiệu lực từ hiện tại trở đi
     * và giao với khoảng chặn [blockStart, blockEnd) của phòng roomId (Spec 11.2 & AC-21, AC-22).
     */
    @EntityGraph(attributePaths = {"room", "organizer", "team", "group"})
    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND b.usageStatus NOT IN (
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.COMPLETED,
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.NO_SHOW,
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.INTERRUPTED
              )
              AND COALESCE(b.releasedAt, b.endTime) > :now
              AND b.startTime < :blockEnd
              AND COALESCE(b.releasedAt, b.endTime) > :blockStart
            ORDER BY b.startTime ASC
            """)
    List<Booking> findAffectedActiveBookingsForRoom(
            @Param("roomId") UUID roomId,
            @Param("blockStart") OffsetDateTime blockStart,
            @Param("blockEnd") OffsetDateTime blockEnd,
            @Param("now") OffsetDateTime now
    );

    /**
     * Tìm các lịch đặt đang giữ hiệu lực từ hiện tại trở đi mà phòng sẽ không còn đáp ứng nếu ngừng phòng hoặc giảm sức chứa (Spec 6.1 & AC-26).
     */
    @EntityGraph(attributePaths = {"room", "organizer"})
    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND b.usageStatus NOT IN (
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.COMPLETED,
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.NO_SHOW,
                  com.tanle.meeting_room_booking.booking.entity.UsageStatus.INTERRUPTED
              )
              AND COALESCE(b.releasedAt, b.endTime) > :now
              AND (:newCapacity IS NULL OR b.attendeeCount > :newCapacity)
            ORDER BY b.startTime ASC
            """)
    List<Booking> findFutureBookingsViolatingRoomChange(
            @Param("roomId") UUID roomId,
            @Param("now") OffsetDateTime now,
            @Param("newCapacity") Integer newCapacity
    );

    // ----- Scheduler queries (Spec 8.5, 24.1 & 25.3) -----

    /**
     * Tự động chuyển các yêu cầu PENDING đã tới giờ bắt đầu (startTime <= now) mà chưa được duyệt sang EXPIRED (Spec 8.5 & AC-08).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking b
            SET b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.EXPIRED,
                b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.NOT_APPLICABLE,
                b.updatedAt = :now,
                b.version = b.version + 1
            WHERE b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.PENDING
              AND b.startTime <= :now
            """)
    int expireOverduePendingBookings(@Param("now") OffsetDateTime now);

    /**
     * Tự động chuyển các lịch đã duyệt (APPROVED / CONFIRMED) nhưng chưa check-in khi đã tới checkInDeadline sang CANCELLED + NO_SHOW (Spec 25.3 & AC-50).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking b
            SET b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.CANCELLED,
                b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.NO_SHOW,
                b.cancellationReasonCode = 'NO_SHOW',
                b.cancelReason = 'Tự động hủy do quá thời hạn check-in (NO_SHOW)',
                b.cancelledAt = COALESCE(b.checkInDeadline, :now),
                b.releasedAt = COALESCE(b.checkInDeadline, :now),
                b.updatedAt = :now,
                b.version = b.version + 1
            WHERE b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.APPROVED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED
              )
              AND b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.NOT_CHECKED_IN
              AND COALESCE(b.checkInDeadline, :cutoff) <= :now
            """)
    int markNoShowBookings(@Param("cutoff") OffsetDateTime cutoff, @Param("now") OffsetDateTime now);

    @EntityGraph(attributePaths = {"room", "organizer"})
    @Query("""
            SELECT b FROM Booking b
            WHERE (b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.IN_USE
                   OR b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS)
              AND b.preEndNotified = false
              AND b.endTime > :now
              AND b.endTime <= :notifyWindowEnd
            """)
    List<Booking> findInProgressNearEnd(
            @Param("now") OffsetDateTime now,
            @Param("notifyWindowEnd") OffsetDateTime notifyWindowEnd
    );

    /**
     * Tự động đóng phiên sử dụng khi đã tới endTime mà người dùng chưa check-out:
     * giữ status = APPROVED (hoặc COMPLETED cho tương thích), usageStatus = COMPLETED, endSource = 'SYSTEM_SCHEDULED_END',
     * không gán checkedOutBy giả lập người dùng (Spec 25.4 & AC-57).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking b
            SET b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.COMPLETED,
                b.endSource = COALESCE(b.endSource, 'SYSTEM_SCHEDULED_END'),
                b.updatedAt = :now,
                b.version = b.version + 1
            WHERE (b.usageStatus = com.tanle.meeting_room_booking.booking.entity.UsageStatus.IN_USE
                   OR b.status = com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS)
              AND b.endTime <= :now
            """)
    int completeExpiredInProgressBookings(@Param("now") OffsetDateTime now);
}
