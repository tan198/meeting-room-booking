package com.tanle.meeting_room_booking.notification.service;

import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.notification.dto.NotificationResponse;
import com.tanle.meeting_room_booking.notification.entity.Notification;
import com.tanle.meeting_room_booking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_DEDUPE_KEY_LENGTH = 120;

    private final NotificationRepository notificationRepository;

    @Transactional
    public void send(UUID userId, UUID bookingId, String type, String title, String message, Map<String, Object> payload, String dedupeKey) {
        String safeDedupeKey = truncate(dedupeKey, MAX_DEDUPE_KEY_LENGTH);
        if (safeDedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, safeDedupeKey)) {
            return;
        }
        Notification notification = Notification.builder()
                .userId(userId)
                .bookingId(bookingId)
                .type(type)
                .title(truncate(title, MAX_TITLE_LENGTH))
                .message(message)
                .payload(payload != null ? payload : Map.of())
                .dedupeKey(safeDedupeKey)
                .build();
        notificationRepository.save(notification);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    @Transactional
    public void sendBulk(Collection<UUID> userIds, UUID bookingId, String type, String title, String message, Map<String, Object> payload, String dedupePrefix) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (UUID userId : userIds) {
            String dedupeKey = dedupePrefix != null ? dedupePrefix + ":" + userId : null;
            send(userId, bookingId, type, title, message, payload, dedupeKey);
        }
    }

    public PageResponse<NotificationResponse> getMyNotifications(UUID userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                notificationRepository.findByUser(userId, unreadOnly, pageable)
                        .map(NotificationResponse::from)
        );
    }

    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));
        if (n.getReadAt() == null) {
            n.setReadAt(OffsetDateTime.now());
            notificationRepository.saveAndFlush(n);
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId, OffsetDateTime.now());
    }
}

