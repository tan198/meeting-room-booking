package com.tanle.meeting_room_booking.booking.service;

import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tác vụ chạy ngầm định kỳ xử lý vòng đời của Booking theo Spec mục 8.5, 24.1 & 25.3:
 * 1. Tự động chuyển PENDING -> EXPIRED nếu đã tới giờ bắt đầu (startTime <= now) mà chưa được duyệt (AC-08).
 * 2. Tự động chuyển APPROVED/CONFIRMED + NOT_CHECKED_IN -> CANCELLED + NO_SHOW nếu quá checkInDeadline (10 phút sau startTime - AC-50).
 * 3. Tự động đóng các phiên đang sử dụng (IN_USE) khi đã tới endTime với endSource = SYSTEM_SCHEDULED_END (AC-57).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingScheduler {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Value("${app.booking.no-show-grace-minutes:10}")
    private long noShowGraceMinutes;

    @Value("${app.booking.pre-end-notify-minutes:10}")
    private long preEndNotifyMinutes;

    @Scheduled(fixedDelayString = "${app.booking.scheduler-interval-ms:60000}")
    @Transactional
    public void processBookingLifecycle() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Tự động chuyển PENDING -> EXPIRED nếu đã tới startTime mà chưa duyệt (Spec 8.5 & AC-08)
        int expiredCount = bookingRepository.expireOverduePendingBookings(now);
        if (expiredCount > 0) {
            log.info("[BookingScheduler] Đã đánh dấu EXPIRED cho {} yêu cầu PENDING quá giờ bắt đầu", expiredCount);
        }

        // 2. Tự động chuyển APPROVED/CONFIRMED (NOT_CHECKED_IN) -> CANCELLED + NO_SHOW nếu quá thời hạn checkInDeadline (Spec 25.3 & AC-50)
        OffsetDateTime noShowCutoff = now.minus(Duration.ofMinutes(noShowGraceMinutes));
        int noShowCount = bookingRepository.markNoShowBookings(noShowCutoff, now);
        if (noShowCount > 0) {
            log.info("[BookingScheduler] Đã đánh dấu CANCELLED + NO_SHOW cho {} lịch đặt phòng quá hạn check-in", noShowCount);
        }

        // 3. Nhắc nhở các cuộc họp IN_USE sắp hết giờ
        OffsetDateTime notifyWindowEnd = now.plus(Duration.ofMinutes(preEndNotifyMinutes));
        List<Booking> endingSoon = bookingRepository.findInProgressNearEnd(now, notifyWindowEnd);
        for (Booking booking : endingSoon) {
            booking.setPreEndNotified(true);
            String dedupeKey = "PRE_END:" + booking.getId() + ":" + booking.getEndTime().toEpochSecond();
            notificationService.send(
                    booking.getOrganizer().getId(),
                    booking.getId(),
                    "BOOKING_PRE_END",
                    "Cuộc họp sắp hết giờ",
                    String.format("Cuộc họp tại phòng %s sẽ kết thúc lúc %s.",
                            booking.getRoom() != null ? booking.getRoom().getName() : "N/A", booking.getEndTime()),
                    Map.of("endTime", booking.getEndTime().toString()),
                    dedupeKey
            );
        }

        // 4. Tự động đóng phiên IN_USE -> COMPLETED (SYSTEM_SCHEDULED_END) khi đã hết giờ (Spec 25.4 & AC-57)
        int completedCount = bookingRepository.completeExpiredInProgressBookings(now);
        if (completedCount > 0) {
            log.info("[BookingScheduler] Đã tự động kết thúc theo giờ (SYSTEM_SCHEDULED_END) {} cuộc họp", completedCount);
        }
    }
}
