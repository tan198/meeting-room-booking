package com.tanle.meeting_room_booking.booking.entity;

import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phiên bản đề nghị thay đổi lịch đã duyệt (APPROVED) theo Spec mục 9:
 * - Lưu ảnh chụp đầy đủ của dữ liệu mới và baseVersion của booking gốc.
 * - Khi chờ duyệt (PENDING), booking gốc vẫn APPROVED và vẫn giữ phòng cũ.
 */
@Entity
@Table(name = "booking_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "base_version", nullable = false, updatable = false)
    private long baseVersion;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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

    @Column(name = "attendee_count", nullable = false)
    @Builder.Default
    private int attendeeCount = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_room_id")
    private Room preferredRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private WorkGroup group;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_department_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> relatedDepartmentIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> participantIds = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_room_id")
    private Room assignedRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private BookingChangeStatus status = BookingChangeStatus.PENDING;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "process_reason", columnDefinition = "TEXT")
    private String processReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

