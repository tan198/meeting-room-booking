package com.tanle.meeting_room_booking.user.service;


import com.tanle.meeting_room_booking.auth.service.RefreshTokenService;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.response.UserResponse;
import com.tanle.meeting_room_booking.role.entity.Role;
import com.tanle.meeting_room_booking.role.repository.RoleRepository;
import com.tanle.meeting_room_booking.user.dto.*;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepo userRepo;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;


    // Admin thao tác
    @Transactional
    public UserResponse createUser(CreateUser rq) {
        String normalizedEmail = rq.email().trim().toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Email '" + normalizedEmail + "' đã tồn tại");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rq.password()));
        user.setFullName(rq.fullName().trim());
        user.setRole(findRoleOrThrow(rq.roleName()));
        user.setActive(true);

        return UserResponse.from(userRepo.save(user));
    }

    public UserResponse getById(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse updateUser(UUID actorId, UUID id, UpdateUser rq) {
        User user = findOrThrow(id);
        Role newRole = findRoleOrThrow(rq.roleName());
        boolean roleChanged = !newRole.equals(user.getRole());

        // Admin không được tự khóa hoặc tự đổi quyền của chính mình
        if (actorId.equals(id) && (!rq.isActive() || roleChanged)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }

        user.setFullName(rq.fullName().trim());
        user.setRole(newRole);
        user.setActive(rq.isActive());

        // Thu hồi toàn bộ refresh token nếu tài khoản bị vô hiệu hóa hoặc thay đổi vai trò
        if (!rq.isActive() || roleChanged) {
            refreshTokenService.revokeAll(id);
        }
        return UserResponse.from(userRepo.saveAndFlush(user));
    }

    @Transactional
    public void deleteUser(UUID actorId, UUID targetId) {
        assertNotSelf(actorId, targetId);

        User user = findOrThrow(targetId);

        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false); // Đảm bảo vô hiệu hóa luôn trạng thái hoạt động
        refreshTokenService.revokeAll(targetId);
    }

    public PageResponse<UserResponse> search(String keyword, String roleName, Boolean isActive, int page, int size) {
        String kw = (keyword == null || keyword.isBlank())
                ? ""
                : escapeLike(keyword.trim().toLowerCase(Locale.ROOT));
        String role = (roleName == null || roleName.isBlank())
                ? null
                : roleName.trim().toUpperCase(Locale.ROOT);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(userRepo.searchUsers(kw, role, isActive, pageable).map(UserResponse::from));
    }

    // User thao tác
    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfile rq) {
        User user = findOrThrow(id);
        user.setFullName(rq.fullName().trim());
        return UserResponse.from(userRepo.saveAndFlush(user));
    }

    @Transactional
    public void changePassword(UUID id, ChangePassword rq) {
        User user = findOrThrow(id);
        if (rq.currentPassword() == null || !passwordEncoder.matches(rq.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        user.setPassword(passwordEncoder.encode(rq.newPassword()));
        refreshTokenService.revokeAll(id);
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

    private Role findRoleOrThrow(String roleName) {
        return roleRepository.findByName(roleName.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò '" + roleName + "'"));
    }
}
