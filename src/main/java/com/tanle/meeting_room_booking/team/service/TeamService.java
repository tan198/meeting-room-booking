package com.tanle.meeting_room_booking.team.service;

import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.team.dto.AddTeamMemberRequest;
import com.tanle.meeting_room_booking.team.dto.CreateTeamRequest;
import com.tanle.meeting_room_booking.team.dto.TeamResponse;
import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.team.entity.UserTeam;
import com.tanle.meeting_room_booking.team.entity.UserTeamId;
import com.tanle.meeting_room_booking.team.repository.TeamRepository;
import com.tanle.meeting_room_booking.team.repository.UserTeamRepository;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final UserRepo userRepo;

    public List<TeamResponse> findAll() {
        List<Team> teams = teamRepository.findAll(Sort.by(Sort.Order.asc("code")));
        if (teams.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        var membersByTeam = userTeamRepository.findById_TeamIdIn(teamIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(ut -> ut.getTeam().getId()));
        return teams.stream()
                .map(t -> TeamResponse.from(t, membersByTeam.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    public List<TeamResponse> findMyTeams(UUID userId) {
        List<UserTeam> memberships = userTeamRepository.findById_UserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Team> teams = memberships.stream()
                .map(UserTeam::getTeam)
                .distinct()
                .toList();
        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        var membersByTeam = userTeamRepository.findById_TeamIdIn(teamIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(ut -> ut.getTeam().getId()));
        return teams.stream()
                .map(t -> TeamResponse.from(t, membersByTeam.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    public TeamResponse getById(UUID id) {
        Team team = findOrThrow(id);
        return TeamResponse.from(team, userTeamRepository.findById_TeamId(id));
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest rq) {
        String code = rq.code().trim().toUpperCase(Locale.ROOT);
        if (teamRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã team '" + code + "' đã tồn tại");
        }

        String type = (rq.type() != null && !rq.type().isBlank())
                ? rq.type().trim().toUpperCase(Locale.ROOT)
                : "TEAM";

        Team team = Team.builder()
                .code(code)
                .name(rq.name().trim())
                .type(type)
                .description(rq.description() != null ? rq.description().trim() : null)
                .build();

        return TeamResponse.from(teamRepository.saveAndFlush(team), List.of());
    }

    @Transactional
    public TeamResponse addMember(UUID teamId, AddTeamMemberRequest rq) {
        Team team = findOrThrow(teamId);
        User user = userRepo.findById(rq.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String role = (rq.memberRole() != null && !rq.memberRole().isBlank())
                ? rq.memberRole().trim()
                : "MEMBER";

        UserTeamId id = new UserTeamId(user.getId(), team.getId());
        UserTeam ut = UserTeam.builder()
                .id(id)
                .user(user)
                .team(team)
                .memberRole(role)
                .build();
        userTeamRepository.saveAndFlush(ut);

        return TeamResponse.from(team, userTeamRepository.findById_TeamId(teamId));
    }

    @Transactional
    public void removeMember(UUID teamId, UUID userId) {
        findOrThrow(teamId);
        userTeamRepository.deleteById(new UserTeamId(userId, teamId));
    }

    public List<TeamResponse.TeamMemberDto> getMembers(UUID teamId) {
        findOrThrow(teamId);
        return userTeamRepository.findById_TeamId(teamId)
                .stream()
                .map(TeamResponse.TeamMemberDto::from)
                .toList();
    }

    public Team findOrThrow(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy Team"));
    }
}
