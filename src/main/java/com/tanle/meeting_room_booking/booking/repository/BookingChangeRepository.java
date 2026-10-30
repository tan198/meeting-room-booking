package com.tanle.meeting_room_booking.booking.repository;

import com.tanle.meeting_room_booking.booking.entity.BookingChange;
import com.tanle.meeting_room_booking.booking.entity.BookingChangeStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingChangeRepository extends JpaRepository<BookingChange, UUID> {

    @EntityGraph(attributePaths = {"booking", "booking.organizer", "booking.room", "preferredRoom", "assignedRoom", "team", "group"})
    Optional<BookingChange> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"booking", "booking.organizer", "preferredRoom", "assignedRoom", "team", "group"})
    List<BookingChange> findByBooking_IdOrderByCreatedAtDesc(UUID bookingId);

    boolean existsByBooking_IdAndStatus(UUID bookingId, BookingChangeStatus status);

    Optional<BookingChange> findByBooking_IdAndStatus(UUID bookingId, BookingChangeStatus status);

    /**
     * Tự động hủy các đề nghị đang PENDING khi booking gốc bị hủy hoặc bị Admin chuyển phòng vận hành (Spec 9 & AC-17).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BookingChange bc
            SET bc.status = com.tanle.meeting_room_booking.booking.entity.BookingChangeStatus.CANCELLED,
                bc.processReason = :reason,
                bc.processedAt = :now,
                bc.updatedAt = :now,
                bc.version = bc.version + 1
            WHERE bc.booking.id = :bookingId
              AND bc.status = com.tanle.meeting_room_booking.booking.entity.BookingChangeStatus.PENDING
            """)
    int cancelPendingChangesForBooking(
            @Param("bookingId") UUID bookingId,
            @Param("reason") String reason,
            @Param("now") OffsetDateTime now
    );

    /**
     * Tự động chuyển các đề nghị PENDING sang EXPIRED khi đã tới giờ bắt đầu của lịch gốc hoặc giờ bắt đầu đề nghị (Spec 9 & AC-17).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BookingChange bc
            SET bc.status = com.tanle.meeting_room_booking.booking.entity.BookingChangeStatus.EXPIRED,
                bc.processReason = 'Đề nghị thay đổi đã hết hạn do tới giờ bắt đầu cuộc họp',
                bc.processedAt = :now,
                bc.updatedAt = :now,
                bc.version = bc.version + 1
            WHERE bc.status = com.tanle.meeting_room_booking.booking.entity.BookingChangeStatus.PENDING
              AND (bc.startTime <= :now OR bc.booking.startTime <= :now)
            """)
    int expireOverduePendingChanges(@Param("now") OffsetDateTime now);
}

