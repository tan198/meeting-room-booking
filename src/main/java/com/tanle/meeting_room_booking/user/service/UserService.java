package com.tanle.meeting_room_booking.user.service;

import com.tanle.meeting_room_booking.auth.service.RefreshTokenService;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.response.UserResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.role.entity.Role;
import com.tanle.meeting_room_booking.role.repository.RoleRepository;
import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.team.entity.UserTeam;
import com.tanle.meeting_room_booking.team.entity.UserTeamId;
import com.tanle.meeting_room_booking.team.repository.TeamRepository;
import com.tanle.meeting_room_booking.team.repository.UserTeamRepository;
import com.tanle.meeting_room_booking.user.dto.*;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepo userRepo;
    private final RoleRepository roleRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    // Admin thao tác
    @Transactional
    public UserResponse createUser(AuthUser actor, CreateUser rq) {
        String normalizedEmail = rq.email().trim().toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Email '" + normalizedEmail + "' đã tồn tại");
        }

        Role targetRole = findRoleWithPermissionsOrThrow(rq.roleName());
        assertCanAssignRole(actor, targetRole, null);

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rq.password()));
        user.setFullName(rq.fullName().trim());
        user.setRole(targetRole);
        user.setActive(true);

        User saved = userRepo.saveAndFlush(user);
        List<UserTeam> assignedTeams = syncUserTeams(saved, rq.teamId(), rq.teamRole(), rq.teams(), false);

        return UserResponse.from(saved, assignedTeams);
    }

    public UserResponse getById(UUID id) {
        User user = findOrThrow(id);
        List<UserTeam> userTeams = userTeamRepository.findById_UserId(id);
        return UserResponse.from(user, userTeams);
    }

    @Transactional
    public UserResponse updateUser(AuthUser actor, UUID id, UpdateUser rq) {
        User user = userRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        Role newRole = findRoleWithPermissionsOrThrow(rq.roleName());
        boolean roleChanged = !newRole.equals(user.getRole());

        // Admin không được tự khóa hoặc tự đổi quyền của chính mình
        if (actor.id().equals(id) && (!rq.isActive() || roleChanged)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }

        if (roleChanged || "ADMIN".equalsIgnoreCase(user.getRole().getName())) {
            assertCanAssignRole(actor, newRole, user.getRole());
        }

        // Bảo vệ không cho phép khóa hoặc hạ quyền tài khoản ADMIN hoạt động cuối cùng (Spec 4.2 & AC-36: LAST_ADMIN_PROTECTED)
        if ("ADMIN".equalsIgnoreCase(user.getRole().getName()) && (!rq.isActive() || roleChanged)) {
            List<UUID> activeAdmins = userRepo.findActiveAdminIds();
            if (activeAdmins.size() <= 1 && activeAdmins.contains(user.getId())) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "Không thể khóa hoặc hạ quyền tài khoản ADMIN hoạt động cuối cùng của hệ thống (LAST_ADMIN_PROTECTED)");
            }
        }

        if (!rq.isActive() && bookingRepository.existsActiveBookingsByOrganizer(id, OffsetDateTime.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Người dùng đang là người tổ chức (Organizer) của lịch họp đang hoạt động. Vui lòng hủy hoặc chuyển lịch họp trước khi vô hiệu hóa tài khoản."
            );
        }

        user.setFullName(rq.fullName().trim());
        user.setRole(newRole);
        user.setActive(rq.isActive());

        // Thu hồi toàn bộ refresh token nếu tài khoản bị vô hiệu hóa hoặc thay đổi vai trò
        if (!rq.isActive() || roleChanged) {
            refreshTokenService.revokeAll(id);
        }
        User saved = userRepo.saveAndFlush(user);

        boolean shouldUpdateTeams = rq.teamId() != null || rq.teams() != null;
        List<UserTeam> userTeams = shouldUpdateTeams
                ? syncUserTeams(saved, rq.teamId(), rq.teamRole(), rq.teams(), true)
                : userTeamRepository.findById_UserId(id);

        return UserResponse.from(saved, userTeams);
    }

    @Transactional
    public void deleteUser(AuthUser actor, UUID targetId) {
        assertNotSelf(actor.id(), targetId);

        User user = userRepo.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        if ("ADMIN".equalsIgnoreCase(user.getRole().getName()) && !"ADMIN".equalsIgnoreCase(actor.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ ADMIN mới có quyền xóa tài khoản ADMIN khác");
        }

        if ("ADMIN".equalsIgnoreCase(user.getRole().getName())) {
            List<UUID> activeAdmins = userRepo.findActiveAdminIds();
            if (activeAdmins.size() <= 1 && activeAdmins.contains(user.getId())) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "Không thể xóa tài khoản ADMIN hoạt động cuối cùng của hệ thống (LAST_ADMIN_PROTECTED)");
            }
        }

        if (bookingRepository.existsActiveBookingsByOrganizer(targetId, OffsetDateTime.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể xóa người dùng đang là người tổ chức (Organizer) của lịch họp đang hoạt động (CONFIRMED / IN_PROGRESS). Vui lòng hủy các lịch họp đó trước."
            );
        }

        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false);
        refreshTokenService.revokeAll(targetId);
    }

    public PageResponse<UserResponse> search(String keyword, String roleName, Boolean isActive, int page, int size) {
        String kw = (keyword == null || keyword.isBlank())
                ? ""
                : escapeLike(keyword.trim().toLowerCase(Locale.ROOT));
        String role = (roleName == null || roleName.isBlank())
                ? null
                : roleName.trim().toUpperCase(Locale.ROOT);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))
        );
        Page<User> usersPage = userRepo.searchUsers(kw, role, isActive, pageable);
        List<UUID> userIds = usersPage.getContent().stream().map(User::getId).toList();
        Map<UUID, List<UserTeam>> teamsByUser = userIds.isEmpty()
                ? Map.of()
                : userTeamRepository.findById_UserIdIn(userIds).stream()
                    .collect(Collectors.groupingBy(ut -> ut.getUser().getId()));

        return PageResponse.from(
                usersPage.map(u -> UserResponse.from(u, teamsByUser.getOrDefault(u.getId(), List.of())))
        );
    }

    // User thao tác
    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfile rq) {
        User user = findOrThrow(id);
        user.setFullName(rq.fullName().trim());
        User saved = userRepo.saveAndFlush(user);
        return UserResponse.from(saved, userTeamRepository.findById_UserId(id));
    }

    @Transactional
    public void changePassword(UUID id, ChangePassword rq) {
        User user = userRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (rq.currentPassword() == null || !passwordEncoder.matches(rq.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        user.setPassword(passwordEncoder.encode(rq.newPassword()));
        refreshTokenService.revokeAll(id);
    }

    /**
     * Chống leo thang đặc quyền (Privilege Escalation):
     * 1. Nếu gán một role khác "USER" (mặc định) hoặc thay đổi role hiện tại của user khác, người thao tác phải có quyền ROLE_MANAGE (hoặc là ADMIN).
     * 2. Chỉ ADMIN mới được phép gán vai trò ADMIN hoặc chỉnh sửa vai trò của một tài khoản ADMIN hiện hữu.
     * 3. Người thao tác không được phép cấp một Role chứa bất kỳ quyền hạn (permission) nào mà chính người thao tác không sở hữu.
     */
    private void assertCanAssignRole(AuthUser actor, Role targetRole, Role currentRole) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        boolean isActorAdmin = "ADMIN".equalsIgnoreCase(actor.role());
        if (isActorAdmin) {
            return;
        }

        // Bảo vệ tuyệt đối vai trò ADMIN
        if ("ADMIN".equalsIgnoreCase(targetRole.getName())
                || (currentRole != null && "ADMIN".equalsIgnoreCase(currentRole.getName()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ quản trị viên tối cao (ADMIN) mới được phép gán hoặc thay đổi vai trò ADMIN");
        }

        // Nếu gán role khác USER mặc định hoặc đổi role của người dùng, bắt buộc phải có quyền ROLE_MANAGE
        boolean isDefaultUserRole = "USER".equalsIgnoreCase(targetRole.getName());
        boolean isChangingExistingRole = currentRole != null && !currentRole.getId().equals(targetRole.getId());
        if ((!isDefaultUserRole || isChangingExistingRole) && !actor.hasAuthority("ROLE_MANAGE")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn cần quyền ROLE_MANAGE để gán hoặc thay đổi vai trò đặc quyền");
        }

        // Không được cấp một Role có chứa permission vượt ngoài tập quyền của chính actor
        Set<String> targetPerms = targetRole.getPermissions() == null
                ? Set.of()
                : targetRole.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> actorPerms = actor.permissions() != null ? actor.permissions() : Set.of();
        if (!actorPerms.containsAll(targetPerms)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không thể gán vai trò có chứa quyền hạn vượt quá quyền hạn hiện tại của bạn");
        }
    }

    private List<UserTeam> syncUserTeams(
            User user,
            UUID singleTeamId,
            String singleTeamRole,
            List<CreateUser.TeamAssignmentDto> teamList,
            boolean replaceExisting) {
        Map<UUID, String> desired = new LinkedHashMap<>();
        if (singleTeamId != null) {
            String role = (singleTeamRole != null && !singleTeamRole.isBlank())
                    ? singleTeamRole.trim()
                    : "MEMBER";
            desired.put(singleTeamId, role);
        }
        if (teamList != null) {
            for (CreateUser.TeamAssignmentDto item : teamList) {
                String role = (item.memberRole() != null && !item.memberRole().isBlank())
                        ? item.memberRole().trim()
                        : "MEMBER";
                desired.put(item.teamId(), role);
            }
        }

        if (replaceExisting) {
            userTeamRepository.deleteById_UserId(user.getId());
            userTeamRepository.flush();
        }

        if (desired.isEmpty()) {
            return List.of();
        }

        Map<UUID, Team> teamsById = teamRepository.findAllById(desired.keySet()).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        if (teamsById.size() != desired.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Một hoặc nhiều Team không tồn tại");
        }

        List<UserTeam> entities = new ArrayList<>(desired.size());
        for (var entry : desired.entrySet()) {
            Team team = teamsById.get(entry.getKey());
            entities.add(UserTeam.builder()
                    .id(new UserTeamId(user.getId(), team.getId()))
                    .user(user)
                    .team(team)
                    .memberRole(entry.getValue())
                    .build());
        }
        return userTeamRepository.saveAllAndFlush(entities);
    }

    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void assertNotSelf(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
    }

    private User findOrThrow(UUID id) {
        return userRepo.findWithRoleById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private Role findRoleWithPermissionsOrThrow(String roleName) {
        return roleRepository.findWithPermissionsByName(roleName.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò '" + roleName + "'"));
    }
}
