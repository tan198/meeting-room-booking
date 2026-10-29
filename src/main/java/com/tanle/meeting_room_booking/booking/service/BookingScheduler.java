package com.tanle.meeting_room_booking.booking.service;

import com.tanle.meeting_room_booking.booking.entity.Booking;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tác vụ chạy ngầm định kỳ xử lý vòng đời của Booking:
 * 1. Đánh dấu NO_SHOW cho các booking CONFIRMED quá hạn check-in (mặc định 15 phút sau start_time).
 * 2. Gửi thông báo nhắc trước khi hết giờ (pre_end_notified) cho các booking IN_PROGRESS sắp kết thúc.
 * 3. Tự động hoàn thành (COMPLETED) các booking IN_PROGRESS đã qua end_time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingScheduler {

    private final BookingRepository bookingRepository;

    @Value("${app.booking.no-show-grace-minutes:15}")
    private long noShowGraceMinutes;

    @Value("${app.booking.pre-end-notify-minutes:10}")
    private long preEndNotifyMinutes;

    @Scheduled(fixedDelayString = "${app.booking.scheduler-interval-ms:60000}")
    @Transactional
    public void processBookingLifecycle() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Tự động chuyển CONFIRMED -> NO_SHOW nếu quá thời gian ân hạn check-in
        OffsetDateTime noShowCutoff = now.minus(Duration.ofMinutes(noShowGraceMinutes));
        int noShowCount = bookingRepository.markNoShowBookings(noShowCutoff, now);
        if (noShowCount > 0) {
            log.info("[BookingScheduler] Đã đánh dấu NO_SHOW cho {} lịch đặt phòng quá hạn check-in", noShowCount);
        }

        // 2. Nhắc nhở các cuộc họp IN_PROGRESS sắp hết giờ
        OffsetDateTime notifyWindowEnd = now.plus(Duration.ofMinutes(preEndNotifyMinutes));
        List<Booking> endingSoon = bookingRepository.findInProgressNearEnd(now, notifyWindowEnd);
        for (Booking booking : endingSoon) {
            booking.setPreEndNotified(true);
            log.info("[BookingScheduler] Nhắc sắp hết giờ cho booking {} (Phòng: '{}', Người đặt: '{}', Kết thúc lúc: {})",
                    booking.getId(),
                    booking.getRoom().getName(),
                    booking.getOrganizer().getEmail(),
                    booking.getEndTime());
        }

        // 3. Tự động chuyển IN_PROGRESS -> COMPLETED khi đã hết giờ
        int completedCount = bookingRepository.completeExpiredInProgressBookings(now);
        if (completedCount > 0) {
            log.info("[BookingScheduler] Đã tự động hoàn thành (COMPLETED) {} cuộc họp hết giờ", completedCount);
        }
    }
}

