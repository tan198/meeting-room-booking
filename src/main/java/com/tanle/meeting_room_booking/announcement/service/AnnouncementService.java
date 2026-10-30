package com.tanle.meeting_room_booking.announcement.service;

import com.tanle.meeting_room_booking.announcement.dto.AnnouncementPreviewResponse;
import com.tanle.meeting_room_booking.announcement.dto.AnnouncementResponse;
import com.tanle.meeting_room_booking.announcement.dto.CreateAnnouncementRequest;
import com.tanle.meeting_room_booking.announcement.dto.PreviewAnnouncementRequest;
import com.tanle.meeting_room_booking.announcement.entity.Announcement;
import com.tanle.meeting_room_booking.announcement.entity.AudienceType;
import com.tanle.meeting_room_booking.announcement.repository.AnnouncementRepository;
import com.tanle.meeting_room_booking.booking.entity.BookingAttendee;
import com.tanle.meeting_room_booking.booking.repository.BookingAttendeeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.group.repository.UserGroupRepository;
import com.tanle.meeting_room_booking.group.repository.WorkGroupRepository;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import com.tanle.meeting_room_booking.team.repository.TeamRepository;
import com.tanle.meeting_room_booking.team.repository.UserTeamRepository;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AnnouncementRepository announcementRepository;
    private final UserRepo userRepo;
    private final WorkGroupRepository workGroupRepository;
    private final UserGroupRepository userGroupRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttendeeRepository attendeeRepository;
    private final NotificationService notificationService;

    /**
     * Xem trước số tài khoản ACTIVE duy nhất nhận thông báo theo phạm vi (POST /api/v1/announcements/preview - Spec 22 UC-04 & 23.5).
     */
    public AnnouncementPreviewResponse previewAudience(PreviewAnnouncementRequest rq) {
        AudienceType type = rq.resolvedType();
        List<UUID> cleanIds = validateAndCleanAudienceIds(type, rq.resolvedIds());
        Set<UUID> recipients = resolveActiveRecipientIds(type, cleanIds);
        return new AnnouncementPreviewResponse(type, cleanIds, recipients.size());
    }

    /**
     * Phát hành thông báo chung (POST /api/v1/announcements - Spec 12.3, 22 UC-04, AC-28, AC-31, AC-41):
     * - Chốt danh sách tài khoản ACTIVE nhận thông báo tại thời điểm phát hành, loại trùng theo userId (AC-28, AC-41).
     * - Nếu không có người nhận ACTIVE hợp lệ -> trả 409 EMPTY_AUDIENCE (UC-04).
     * - Không tự thay đổi trạng thái trống của phòng hoặc trạng thái lịch đặt (AC-31).
     */
    @Transactional
    public AnnouncementResponse publish(AuthUser admin, CreateAnnouncementRequest rq) {
        AudienceType type = rq.resolvedType();
        List<UUID> cleanIds = validateAndCleanAudienceIds(type, rq.resolvedIds());

        if (rq.correctionOfId() != null && !announcementRepository.existsById(rq.correctionOfId())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND, "Thông báo gốc cần đính chính (correctionOfId) không tồn tại");
        }

        Set<UUID> recipients = resolveActiveRecipientIds(type, cleanIds);
        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_AUDIENCE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Announcement announcement = Announcement.builder()
                .title(rq.title().trim())
                .content(rq.content().trim())
                .audienceType(type)
                .audienceIds(cleanIds)
                .recipientIds(new ArrayList<>(recipients))
                .recipientCount(recipients.size())
                .relatedType(rq.relatedType() != null ? rq.relatedType().trim() : null)
                .relatedId(rq.relatedId())
                .correctionOfId(rq.correctionOfId())
                .publishedBy(admin.id())
                .publishedAt(now)
                .build();

        Announcement saved = announcementRepository.saveAndFlush(announcement);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("announcementId", saved.getId().toString());
        payload.put("audienceType", type.name());
        if (saved.getRelatedType() != null) {
            payload.put("relatedType", saved.getRelatedType());
        }
        if (saved.getRelatedId() != null) {
            payload.put("relatedId", saved.getRelatedId().toString());
        }
        if (saved.getCorrectionOfId() != null) {
            payload.put("correctionOfId", saved.getCorrectionOfId().toString());
        }

        UUID bookingRef = "BOOKING".equalsIgnoreCase(saved.getRelatedType()) ? saved.getRelatedId() : null;
        notificationService.sendBulk(
                recipients,
                bookingRef,
                "ANNOUNCEMENT",
                saved.getTitle(),
                saved.getContent(),
                payload,
                "ANNOUNCEMENT:" + saved.getId()
        );

        return AnnouncementResponse.from(saved);
    }

    public AnnouncementResponse getById(UUID id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        return AnnouncementResponse.from(a);
    }

    public PageResponse<AnnouncementResponse> listAll(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Announcement> result = announcementRepository.findAllByOrderByPublishedAtDesc(
                PageRequest.of(Math.max(page, 0), safeSize)
        );
        return PageResponse.from(result.map(AnnouncementResponse::from));
    }

    private List<UUID> validateAndCleanAudienceIds(AudienceType type, List<UUID> rawIds) {
        if (type == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại phạm vi nhận thông báo (audience.type) là bắt buộc");
        }
        if (type == AudienceType.ALL) {
            if (rawIds != null && !rawIds.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phạm vi ALL không được truyền danh sách ids");
            }
            return List.of();
        }

        if (rawIds == null || rawIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phạm vi " + type + " bắt buộc phải có ít nhất 1 UUID trong danh sách ids");
        }

        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : rawIds) {
            if (id == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách ids không được chứa phần tử null");
            }
            if (!unique.add(id)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách ids không được chứa phần tử trùng lặp: " + id);
            }
        }
        return new ArrayList<>(unique);
    }

    private Set<UUID> resolveActiveRecipientIds(AudienceType type, List<UUID> ids) {
        return switch (type) {
            case ALL -> new LinkedHashSet<>(userRepo.findAllActiveUserIds());
            case DEPARTMENTS, PROJECTS -> {
                long existingCount = workGroupRepository.countByIdIn(ids);
                if (existingCount != ids.size()) {
                    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Một hoặc nhiều Phòng ban / Dự án trong phạm vi không tồn tại");
                }
                yield new LinkedHashSet<>(userGroupRepository.findActiveUserIdsByGroupIds(ids));
            }
            case TEAMS -> {
                long existingCount = teamRepository.countByIdIn(ids);
                if (existingCount != ids.size()) {
                    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Một hoặc nhiều Team trong phạm vi không tồn tại");
                }
                yield new LinkedHashSet<>(userTeamRepository.findActiveUserIdsByTeamIds(ids));
            }
            case BOOKINGS -> {
                var bookings = bookingRepository.findAllById(ids);
                if (bookings.size() != ids.size()) {
                    throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Một hoặc nhiều Booking trong phạm vi không tồn tại");
                }
                Set<UUID> activeRecipients = new LinkedHashSet<>();
                for (var b : bookings) {
                    if (b.getOrganizer() != null && b.getOrganizer().isActive()) {
                        activeRecipients.add(b.getOrganizer().getId());
                    }
                }
                for (BookingAttendee att : attendeeRepository.findById_BookingIdIn(ids)) {
                    if (att.getUser() != null && att.getUser().isActive()) {
                        activeRecipients.add(att.getUser().getId());
                    }
                }
                yield activeRecipients;
            }
        };
    }
}

