package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.*;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.team.entity.Team;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID roomId,
        String roomName,
        Integer roomFloor,
        Integer roomCapacity,
        UUID preferredRoomId,
        String preferredRoomName,
        UUID teamId,
        String teamCode,
        String teamName,
        UUID groupId,
        String groupCode,
        String groupName,
        String groupType,
        UUID organizerDepartmentId,
        List<UUID> relatedDepartmentIds,
        UUID organizerId,
        String organizerName,
        String organizerEmail,
        String title,
        String purpose,
        String description,
        String note,
        int attendeeCount,
        MeetingType meetingType,
        Visibility visibility,
        boolean isConfidential,
        MeetingMode meetingMode,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime actualStartTime,
        OffsetDateTime actualEndTime,
        OffsetDateTime releasedAt,
        BookingStatus status,
        UsageStatus usageStatus,
        OffsetDateTime checkInDeadline,
        UUID checkedInBy,
        UUID checkedOutBy,
        String evidenceType,
        String endSource,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        String approvalNote,
        UUID rejectedBy,
        OffsetDateTime rejectedAt,
        String rejectReason,
        List<AttendeeResponse> attendees,
        UUID cancelledBy,
        OffsetDateTime cancelledAt,
        String cancellationReasonCode,
        String cancelReason,
        List<String> warnings,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    private static final String CONFIDENTIAL_MASK = "Cuộc họp riêng tư";

    public static BookingResponse from(Booking b, List<BookingAttendee> attendees, AuthUser viewer) {
        return from(b, attendees, viewer, List.of());
    }

    public static BookingResponse from(Booking b, List<BookingAttendee> attendees, AuthUser viewer, List<String> warnings) {
        boolean isParticipant = isParticipantOrOrganizer(b, attendees, viewer);
        boolean isAdmin = viewer != null && ("ADMIN".equalsIgnoreCase(viewer.role()) || viewer.hasAuthority("BOOKING_MANAGE"));
        boolean isConfidential = b.getVisibility() == Visibility.CONFIDENTIAL || b.isConfidential();

        // Quyền xem chi tiết nội dung (title, purpose/description, note, meetingType nhạy cảm, team, project, attendees):
        // - STANDARD: Chủ lịch, người tham dự hoặc Admin đều được xem đầy đủ; nhân viên ngoài chỉ thấy giờ bận, phòng và tên người tổ chức (Spec 7.3 & 27.5).
        // - CONFIDENTIAL: Chỉ Chủ lịch hoặc người tham dự được xem nội dung; Admin vận hành không tham dự chỉ thấy dữ liệu vận hành (Spec 27.5).
        boolean canViewFullContent = isParticipant || (!isConfidential && isAdmin);
        boolean canViewOperationalMeta = isParticipant || isAdmin || !isConfidential;

        Room assignedRoom = b.getRoom();
        Room preferredRoom = b.getPreferredRoom();
        Team team = canViewFullContent ? b.getTeam() : null;
        WorkGroup group = canViewFullContent ? b.getGroup() : null;

        String displayTitle = canViewFullContent ? b.getTitle() : (isConfidential ? CONFIDENTIAL_MASK : "Phòng đã được đặt");
        String displayDesc = canViewFullContent ? b.getDescription() : null;
        String displayNote = canViewFullContent ? b.getNote() : null;
        MeetingType displayMeetingType = canViewFullContent ? b.getMeetingType() : null;
        List<UUID> displayRelatedDepts = canViewFullContent && b.getRelatedDepartmentIds() != null
                ? b.getRelatedDepartmentIds()
                : List.of();
        List<AttendeeResponse> attendeeList = (canViewFullContent && attendees != null)
                ? attendees.stream().map(AttendeeResponse::from).toList()
                : List.of();

        return new BookingResponse(
                b.getId(),
                assignedRoom != null ? assignedRoom.getId() : null,
                assignedRoom != null ? assignedRoom.getName() : null,
                assignedRoom != null ? assignedRoom.getFloor() : null,
                assignedRoom != null ? assignedRoom.getCapacity() : null,
                preferredRoom != null ? preferredRoom.getId() : null,
                preferredRoom != null ? preferredRoom.getName() : null,
                team != null ? team.getId() : null,
                team != null ? team.getCode() : null,
                team != null ? team.getName() : null,
                group != null ? group.getId() : null,
                group != null ? group.getCode() : null,
                group != null ? group.getName() : null,
                group != null ? group.getType() : null,
                canViewOperationalMeta ? b.getOrganizerDepartmentId() : null,
                displayRelatedDepts,
                canViewOperationalMeta ? b.getOrganizer().getId() : null,
                canViewOperationalMeta ? b.getOrganizer().getFullName() : null,
                canViewOperationalMeta ? b.getOrganizer().getEmail() : null,
                displayTitle,
                displayDesc,
                displayDesc,
                displayNote,
                b.getAttendeeCount(),
                displayMeetingType,
                b.getVisibility(),
                isConfidential,
                b.getMeetingMode(),
                b.getStartTime(),
                b.getEndTime(),
                b.getActualStartTime(),
                b.getActualEndTime(),
                b.getReleasedAt(),
                b.getStatus(),
                b.getUsageStatus(),
                b.getCheckInDeadline(),
                canViewOperationalMeta ? b.getCheckedInBy() : null,
                canViewOperationalMeta ? b.getCheckedOutBy() : null,
                canViewOperationalMeta ? b.getEvidenceType() : null,
                canViewOperationalMeta ? b.getEndSource() : null,
                canViewOperationalMeta ? b.getApprovedBy() : null,
                b.getApprovedAt(),
                canViewOperationalMeta ? b.getApprovalNote() : null,
                canViewOperationalMeta ? b.getRejectedBy() : null,
                b.getRejectedAt(),
                canViewFullContent ? b.getRejectReason() : null,
                attendeeList,
                canViewFullContent ? b.getCancelledBy() : null,
                b.getCancelledAt(),
                canViewOperationalMeta ? b.getCancellationReasonCode() : null,
                canViewFullContent ? b.getCancelReason() : null,
                warnings != null ? warnings : List.of(),
                b.getVersion(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }

    private static boolean isParticipantOrOrganizer(Booking b, List<BookingAttendee> attendees, AuthUser viewer) {
        if (viewer == null) {
            return false;
        }
        if (b.getOrganizer().getId().equals(viewer.id())) {
            return true;
        }
        if (attendees != null) {
            return attendees.stream().anyMatch(a -> a.getUser().getId().equals(viewer.id()));
        }
        return false;
    }
}
