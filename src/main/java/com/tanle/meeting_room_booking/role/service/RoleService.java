package com.tanle.meeting_room_booking.role.service;

import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.permission.repository.PermissionRepository;
import com.tanle.meeting_room_booking.role.dto.CreateRoleRequest;
import com.tanle.meeting_room_booking.role.dto.RoleResponse;
import com.tanle.meeting_room_booking.role.dto.UpdateRoleRequest;
import com.tanle.meeting_room_booking.role.entity.Role;
import com.tanle.meeting_room_booking.role.repository.RoleRepository;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private static final Set<String> PROTECTED_ROLES = Set.of("ADMIN", "USER");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepo userRepo;

    public List<RoleResponse> findAll() {
        return roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
    }

    public RoleResponse getById(UUID id) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        return RoleResponse.from(role);
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest rq) {
        String normalizedName = rq.name().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByName(normalizedName)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Vai trò '" + normalizedName + "' đã tồn tại");
        }

        Set<Permission> permissions = resolvePermissions(rq.permissionCodes());

        Role role = Role.builder()
                .name(normalizedName)
                .description(rq.description() != null ? rq.description().trim() : null)
                .permissions(permissions)
                .build();

        return RoleResponse.from(roleRepository.saveAndFlush(role));
    }

    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest rq) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(role.getName())) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ROLE_PROTECTED,
                    "Không thể thu hồi hoặc thay đổi quyền của vai trò ADMIN"
            );
        }

        Set<Permission> permissions = resolvePermissions(rq.permissionCodes());

        if (rq.description() != null) {
            role.setDescription(rq.description().trim());
        }
        role.setPermissions(permissions);

        return RoleResponse.from(roleRepository.saveAndFlush(role));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        if (PROTECTED_ROLES.contains(role.getName().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_PROTECTED);
        }

        if (userRepo.existsByRole_Id(id)) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }

        roleRepository.delete(role);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> normalizedCodes = codes.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Permission> found = permissionRepository.findByCodeIn(normalizedCodes);
        if (found.size() != normalizedCodes.size()) {
            Set<String> foundCodes = found.stream().map(Permission::getCode).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(normalizedCodes);
            missing.removeAll(foundCodes);
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND, "Các mã quyền không tồn tại: " + missing);
        }
        return new HashSet<>(found);
    }
}

