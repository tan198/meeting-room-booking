package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.MeetingType;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UpdateBookingRequest(
        /** Phiên bản kỳ vọng của hồ sơ để chống ghi đè đồng thời (theo Spec 23.1). */
        Long expectedVersion,

        UUID roomId,
        UUID preferredRoomId,

        UUID teamId,
        UUID organizingTeamId,

        UUID groupId,
        UUID projectId,

        Set<UUID> relatedDepartmentIds,

        @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
        @Size(max = 200, message = "Tiêu đề cuộc họp tối đa 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mục đích cuộc họp tối đa 2000 ký tự")
        String purpose,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String note,

        MeetingType meetingType,

        Visibility visibility,

        Boolean isConfidential,

        OffsetDateTime startTime,
        OffsetDateTime startAt,

        OffsetDateTime endTime,
        OffsetDateTime endAt,

        @Min(value = 1, message = "Số người tham dự phải lớn hơn 0")
        Integer attendeeCount,

        Set<UUID> attendeeIds,
        Set<UUID> participantIds,

        Set<UUID> optionalAttendeeIds,

        Boolean inviteAllScopeMembers
) {
    public UUID resolvedPreferredRoomId() {
        return preferredRoomId != null ? preferredRoomId : roomId;
    }

    public UUID resolvedTeamId() {
        return organizingTeamId != null ? organizingTeamId : teamId;
    }

    public UUID resolvedGroupId() {
        return projectId != null ? projectId : groupId;
    }

    public OffsetDateTime resolvedStartTime() {
        return startAt != null ? startAt : startTime;
    }

    public OffsetDateTime resolvedEndTime() {
        return endAt != null ? endAt : endTime;
    }

    public String resolvedPurpose() {
        if (purpose != null && !purpose.isBlank()) {
            return purpose.trim();
        }
        return description != null && !description.isBlank() ? description.trim() : null;
    }

    public Set<UUID> resolvedParticipantIds() {
        if (participantIds != null && !participantIds.isEmpty()) {
            return participantIds;
        }
        return attendeeIds;
    }
}
