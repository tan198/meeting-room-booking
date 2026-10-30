package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.*;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.team.entity.Team;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BookingChangeResponse(
        UUID id,
        UUID bookingId,
        long baseVersion,
        UUID organizerId,
        String organizerName,
        String title,
        String purpose,
        String note,
        MeetingType meetingType,
        Visibility visibility,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int attendeeCount,
        UUID preferredRoomId,
        String preferredRoomName,
        UUID assignedRoomId,
        String assignedRoomName,
        UUID teamId,
        String teamName,
        UUID groupId,
        String groupName,
        List<UUID> relatedDepartmentIds,
        List<UUID> participantIds,
        BookingChangeStatus status,
        UUID processedBy,
        OffsetDateTime processedAt,
        String processReason,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    private static final String CONFIDENTIAL_MASK = "Cuộc họp riêng tư";

    public static BookingChangeResponse from(BookingChange bc, AuthUser viewer) {
        Booking booking = bc.getBooking();
        boolean isOrganizerOrParticipant = viewer != null && (
                booking.getOrganizer().getId().equals(viewer.id())
                        || (bc.getParticipantIds() != null && bc.getParticipantIds().contains(viewer.id()))
        );
        boolean isAdmin = viewer != null && ("ADMIN".equalsIgnoreCase(viewer.role()) || viewer.hasAuthority("BOOKING_MANAGE"));

        // Theo Spec 9 & 27.6: áp dụng chính sách riêng tư chặt hơn giữa lịch gốc và đề nghị thay đổi.
        // Nếu là CONFIDENTIAL, Admin vận hành không tham dự chỉ xem được dữ liệu bố trí vận hành, ẩn title/purpose/note/meetingType/team/group/participants.
        boolean isConfidential = bc.getVisibility() == Visibility.CONFIDENTIAL
                || booking.getVisibility() == Visibility.CONFIDENTIAL
                || booking.isConfidential();
        boolean canViewFullContent = isOrganizerOrParticipant || (!isConfidential && isAdmin);

        Room prefRoom = bc.getPreferredRoom();
        Room assignedRoom = bc.getAssignedRoom();
        Team team = canViewFullContent ? bc.getTeam() : null;
        WorkGroup group = canViewFullContent ? bc.getGroup() : null;

        return new BookingChangeResponse(
                bc.getId(),
                booking.getId(),
                bc.getBaseVersion(),
                booking.getOrganizer().getId(),
                booking.getOrganizer().getFullName(),
                canViewFullContent ? bc.getTitle() : CONFIDENTIAL_MASK,
                canViewFullContent ? bc.getPurpose() : null,
                canViewFullContent ? bc.getNote() : null,
                canViewFullContent ? bc.getMeetingType() : null,
                isConfidential ? Visibility.CONFIDENTIAL : bc.getVisibility(),
                bc.getStartTime(),
                bc.getEndTime(),
                bc.getAttendeeCount(),
                prefRoom != null ? prefRoom.getId() : null,
                prefRoom != null ? prefRoom.getName() : null,
                assignedRoom != null ? assignedRoom.getId() : null,
                assignedRoom != null ? assignedRoom.getName() : null,
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                group != null ? group.getId() : null,
                group != null ? group.getName() : null,
                canViewFullContent && bc.getRelatedDepartmentIds() != null ? bc.getRelatedDepartmentIds() : List.of(),
                canViewFullContent && bc.getParticipantIds() != null ? bc.getParticipantIds() : List.of(),
                bc.getStatus(),
                bc.getProcessedBy(),
                bc.getProcessedAt(),
                canViewFullContent ? bc.getProcessReason() : null,
                bc.getVersion(),
                bc.getCreatedAt(),
                bc.getUpdatedAt()
        );
    }
}

