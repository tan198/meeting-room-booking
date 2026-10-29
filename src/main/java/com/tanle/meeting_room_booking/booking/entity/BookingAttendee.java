package com.tanle.meeting_room_booking.booking.entity;

import com.tanle.meeting_room_booking.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_attendees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingAttendee {

    @EmbeddedId
    private BookingAttendeeId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookingId")
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_role", length = 20, nullable = false)
    @Builder.Default
    private AttendeeRole attendeeRole = AttendeeRole.REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", length = 20, nullable = false)
    @Builder.Default
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;

    @Column(name = "decline_reason", length = 255)
    private String declineReason;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;
}

