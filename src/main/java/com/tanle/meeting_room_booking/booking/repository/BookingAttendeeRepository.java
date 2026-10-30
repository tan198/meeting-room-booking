package com.tanle.meeting_room_booking.booking.repository;

import com.tanle.meeting_room_booking.booking.entity.BookingAttendee;
import com.tanle.meeting_room_booking.booking.entity.BookingAttendeeId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingAttendeeRepository extends JpaRepository<BookingAttendee, BookingAttendeeId> {

    @EntityGraph(attributePaths = {"user"})
    List<BookingAttendee> findById_BookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"user"})
    List<BookingAttendee> findById_BookingIdIn(Collection<UUID> bookingIds);

    /**
     * Tìm các thành viên bị trùng lịch họp khác (CONFIRMED / IN_PROGRESS) và chưa từ chối (rsvp_status <> DECLINED)
     * trong khoảng thời gian [startTime, endTime).
     */
    @Query("""
            SELECT DISTINCT ba.user.email FROM BookingAttendee ba
            JOIN ba.booking b
            WHERE ba.user.id IN :userIds
              AND ba.rsvpStatus <> com.tanle.meeting_room_booking.booking.entity.RsvpStatus.DECLINED
              AND b.status IN (
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.CONFIRMED,
                  com.tanle.meeting_room_booking.booking.entity.BookingStatus.IN_PROGRESS
              )
              AND b.startTime < :endTime
              AND b.endTime > :startTime
              AND (:excludeBookingId IS NULL OR b.id <> :excludeBookingId)
            """)
    List<String> findConflictingAttendeeEmails(
            @Param("userIds") Collection<UUID> userIds,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("excludeBookingId") UUID excludeBookingId
    );
}

