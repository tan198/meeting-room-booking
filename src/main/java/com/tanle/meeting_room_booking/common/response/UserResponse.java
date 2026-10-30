package com.tanle.meeting_room_booking.common.response;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.team.entity.UserTeam;
import com.tanle.meeting_room_booking.user.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String roleName,
        List<UserTeamRoleDto> teams,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record UserTeamRoleDto(
            UUID teamId,
            String teamCode,
            String teamName,
            String memberRole
    ) {
        public static UserTeamRoleDto from(UserTeam ut) {
            return new UserTeamRoleDto(
                    ut.getTeam().getId(),
                    ut.getTeam().getCode(),
                    ut.getTeam().getName(),
                    ut.getMemberRole()
            );
        }
    }

    public static UserResponse from(User user) {
        return from(user, List.of());
    }

    public static UserResponse from(User user, List<UserTeam> userTeams) {
        List<String> perms = user.getRole() != null && user.getRole().getPermissions() != null
                ? user.getRole().getPermissions().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .toList()
                : List.of();
        List<UserTeamRoleDto> teamDtos = userTeams != null
                ? userTeams.stream().map(UserTeamRoleDto::from).toList()
                : List.of();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                teamDtos,
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
