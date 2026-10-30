package com.tanle.meeting_room_booking.booking.repository;

import com.tanle.meeting_room_booking.booking.entity.BookingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingAuditLogRepository extends JpaRepository<BookingAuditLog, UUID> {

    List<BookingAuditLog> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}

