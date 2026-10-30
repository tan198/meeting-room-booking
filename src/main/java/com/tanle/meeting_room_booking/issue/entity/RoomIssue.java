package com.tanle.meeting_room_booking.issue.entity;

import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @Column(name = "related_booking_id")
    private UUID relatedBookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private IssueCategory category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_impact", length = 20, nullable = false)
    private IssueImpact proposedImpact;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_impact", length = 20)
    private IssueImpact actualImpact;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private IssueStatus status = IssueStatus.OPEN;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

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

