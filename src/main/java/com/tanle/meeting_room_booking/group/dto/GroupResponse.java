package com.tanle.meeting_room_booking.group.dto;

import com.tanle.meeting_room_booking.group.entity.UserGroup;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String code,
        String name,
        String type,
        String description,
        OffsetDateTime createdAt,
        List<GroupMemberDto> members
) {
    public record GroupMemberDto(
            UUID userId,
            String email,
            String fullName,
            OffsetDateTime joinedAt
    ) {
        public static GroupMemberDto from(UserGroup ug) {
            return new GroupMemberDto(
                    ug.getUser().getId(),
                    ug.getUser().getEmail(),
                    ug.getUser().getFullName(),
                    ug.getJoinedAt()
            );
        }
    }

    public static GroupResponse from(WorkGroup group, List<UserGroup> members) {
        List<GroupMemberDto> memberDtos = members != null
                ? members.stream().map(GroupMemberDto::from).toList()
                : List.of();
        return new GroupResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                group.getType(),
                group.getDescription(),
                group.getCreatedAt(),
                memberDtos
        );
    }
}
