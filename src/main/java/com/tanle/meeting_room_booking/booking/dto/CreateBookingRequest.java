package com.tanle.meeting_room_booking.booking.dto;

import com.tanle.meeting_room_booking.booking.entity.MeetingMode;
import com.tanle.meeting_room_booking.booking.entity.MeetingType;
import com.tanle.meeting_room_booking.booking.entity.Visibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CreateBookingRequest(
        /** Phòng mong muốn (hoặc tương thích ngược qua roomId). Có thể để trống khi gửi yêu cầu PENDING để Admin phân phòng. */
        UUID roomId,
        UUID preferredRoomId,

        /** Team tổ chức cuộc họp (nếu có). Hỗ trợ cả tên trường teamId và organizingTeamId theo Spec. */
        UUID teamId,
        UUID organizingTeamId,

        /** Dự án / Phòng ban liên quan (groupId hoặc projectId theo Spec). */
        UUID groupId,
        UUID projectId,

        /** Danh sách ID các phòng ban phối hợp trong cuộc họp (theo Spec 8.1 & 27.3). */
        Set<UUID> relatedDepartmentIds,

        @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
        @Size(max = 200, message = "Tiêu đề cuộc họp tối đa 200 ký tự")
        String title,

        /** Mục đích cuộc họp (purpose theo Spec hoặc description tương thích ngược, tối đa 2000 ký tự). */
        @Size(max = 2000, message = "Mục đích cuộc họp tối đa 2000 ký tự")
        String purpose,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String note,

        /** Mục đích / loại cuộc họp theo Spec 27.4 (GENERAL, SYNC, PLANNING, REVIEW, SALARY_REVIEW, PERFORMANCE_REVIEW, INTERVIEW, TRAINING, OTHER). */
        MeetingType meetingType,

        /** Mức riêng tư theo Spec 27.5 (STANDARD, CONFIDENTIAL). Các loại nhạy cảm sẽ tự động nâng lên CONFIDENTIAL. */
        Visibility visibility,

        Boolean isConfidential,

        MeetingMode meetingMode,

        /** Thời gian bắt đầu (hỗ trợ cả startTime và startAt theo Spec). */
        OffsetDateTime startTime,
        OffsetDateTime startAt,

        /** Thời gian kết thúc (hỗ trợ cả endTime và endAt theo Spec). */
        OffsetDateTime endTime,
        OffsetDateTime endAt,

        @Min(value = 1, message = "Số người tham dự phải lớn hơn hoặc bằng 1")
        Integer attendeeCount,

        /** Danh sách thành viên nội bộ được mời cụ thể (hỗ trợ cả attendeeIds và participantIds theo Spec). */
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
