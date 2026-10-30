package com.tanle.meeting_room_booking.announcement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", length = 30, nullable = false)
    private AudienceType audienceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> audienceIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> recipientIds = new ArrayList<>();

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "correction_of_id")
    private UUID correctionOfId;

    @Column(name = "published_by", nullable = false, updatable = false)
    private UUID publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private OffsetDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

