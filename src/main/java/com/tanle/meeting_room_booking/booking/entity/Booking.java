package com.tanle.meeting_room_booking.booking.entity;

import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Phòng chính thức được phân bổ (có thể null khi đang ở trạng thái PENDING chờ duyệt). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /** Phòng mong muốn do người tổ chức đề xuất khi gửi yêu cầu PENDING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_room_id")
    private Room preferredRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    /** Team tổ chức cuộc họp (nếu có). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Dự án / Phòng ban liên quan (groupId / projectId). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private WorkGroup group;

    /** Đơn vị (Phòng ban) chủ trì của người tổ chức tại thời điểm gửi yêu cầu. */
    @Column(name = "organizer_department_id")
    private UUID organizerDepartmentId;

    /** Danh sách ID các phòng ban phối hợp trong cuộc họp (lưu snapshot). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_department_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> relatedDepartmentIds = new ArrayList<>();

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "attendee_count", nullable = false)
    @Builder.Default
    private int attendeeCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", length = 30, nullable = false)
    @Builder.Default
    private MeetingType meetingType = MeetingType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.STANDARD;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "actual_start_time")
    private OffsetDateTime actualStartTime;

    @Column(name = "actual_end_time")
    private OffsetDateTime actualEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_status", length = 25, nullable = false)
    @Builder.Default
    private UsageStatus usageStatus = UsageStatus.NOT_APPLICABLE;

    @Column(name = "check_in_deadline")
    private OffsetDateTime checkInDeadline;

    @Column(name = "checked_in_by")
    private UUID checkedInBy;

    @Column(name = "checked_out_by")
    private UUID checkedOutBy;

    @Column(name = "evidence_type", length = 30)
    private String evidenceType;

    @Column(name = "end_source", length = 30)
    private String endSource;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "cancellation_reason_code", length = 40)
    private String cancellationReasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_mode", length = 20, nullable = false)
    @Builder.Default
    private MeetingMode meetingMode = MeetingMode.STANDARD;

    @Column(name = "is_confidential", nullable = false)
    @Builder.Default
    private boolean confidential = false;

    @Column(name = "parent_booking_id")
    private UUID parentBookingId;

    @Column(name = "pre_end_notified", nullable = false)
    @Builder.Default
    private boolean preEndNotified = false;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public OffsetDateTime getEffectiveOccupiedUntil() {
        if (releasedAt != null && releasedAt.isBefore(endTime)) {
            return releasedAt.isAfter(startTime) ? releasedAt : startTime;
        }
        return endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
