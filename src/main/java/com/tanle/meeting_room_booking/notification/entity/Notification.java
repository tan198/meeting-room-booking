package com.tanle.meeting_room_booking.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Column(name = "type", length = 40, nullable = false, updatable = false)
    private String type;

    @Column(name = "title", length = 300, nullable = false, updatable = false)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    @Builder.Default
    private Map<String, Object> payload = Map.of();

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "dedupe_key", length = 120, updatable = false)
    private String dedupeKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

