package com.tanle.meeting_room_booking.team.dto;

import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.team.entity.UserTeam;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String code,
        String name,
        String type,
        String description,
        OffsetDateTime createdAt,
        List<TeamMemberDto> members
) {
    public record TeamMemberDto(
            UUID userId,
            String email,
            String fullName,
            String memberRole
    ) {
        public static TeamMemberDto from(UserTeam ut) {
            return new TeamMemberDto(
                    ut.getUser().getId(),
                    ut.getUser().getEmail(),
                    ut.getUser().getFullName(),
                    ut.getMemberRole()
            );
        }
    }

    public static TeamResponse from(Team team, List<UserTeam> members) {
        List<TeamMemberDto> memberDtos = members != null
                ? members.stream().map(TeamMemberDto::from).toList()
                : List.of();
        return new TeamResponse(
                team.getId(),
                team.getCode(),
                team.getName(),
                team.getType(),
                team.getDescription(),
                team.getCreatedAt(),
                memberDtos
        );
    }
}
