package com.tanle.meeting_room_booking.common.config;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.permission.repository.PermissionRepository;
import com.tanle.meeting_room_booking.role.entity.Role;
import com.tanle.meeting_room_booking.role.repository.RoleRepository;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the base roles, permissions, and (optionally) the first admin account. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Map<String, String> DEFAULT_ROLES = Map.of(
            "ADMIN", "Quản trị hệ thống",
            "USER", "Người dùng thông thường"
    );

    private static final Map<String, String> DEFAULT_PERMISSIONS;

    static {
        Map<String, String> perms = new LinkedHashMap<>();
        perms.put("USER_READ", "Xem danh sách và chi tiết người dùng");
        perms.put("USER_CREATE", "Tạo người dùng");
        perms.put("USER_UPDATE", "Cập nhật người dùng");
        perms.put("USER_DELETE", "Xóa người dùng");
        perms.put("ROOM_CREATE", "Tạo phòng họp mới");
        perms.put("ROOM_UPDATE", "Cập nhật thông tin và trạng thái phòng họp");
        perms.put("ROOM_DELETE", "Xóa mềm phòng họp");
        perms.put("BOOKING_MANAGE", "Quản lý toàn bộ lịch đặt phòng của hệ thống");
        perms.put("ROLE_MANAGE", "Quản lý vai trò và gán quyền hạn (RBAC)");
        perms.put("TEAM_MANAGE", "Quản lý Team chuyên môn và vị trí thành viên");
        perms.put("GROUP_MANAGE", "Quản lý Group (Phòng ban / Dự án) và phân bổ thành viên");
        DEFAULT_PERMISSIONS = Map.copyOf(perms);
    }

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@meetingroom.local}")
    private String adminEmail;

    @Value("${app.admin.secondary-email:admin2@meetingroom.local}")
    private String secondaryAdminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:System Admin}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(String... args) {
        DEFAULT_PERMISSIONS.forEach((code, description) -> {
            if (!permissionRepository.existsByCode(code)) {
                permissionRepository.save(Permission.builder().code(code).description(description).build());
            }
        });

        DEFAULT_ROLES.forEach((name, description) -> {
            if (!roleRepository.existsByName(name)) {
                roleRepository.save(Role.builder().name(name).description(description).build());
            }
        });

        // Đảm bảo vai trò ADMIN luôn có đầy đủ tất cả quyền hệ thống
        Role adminRole = roleRepository.findWithPermissionsByName("ADMIN").orElseThrow();
        Set<Permission> allPerms = Set.copyOf(permissionRepository.findAll());
        if (!adminRole.getPermissions().containsAll(allPerms)) {
            adminRole.getPermissions().addAll(allPerms);
            roleRepository.saveAndFlush(adminRole);
        }

        seedAdmin(adminRole, adminEmail, adminFullName);
        // Theo Spec mục 4.2 & 18: Cần tối thiểu 2 tài khoản Admin hoạt động để Admin này có thể phê duyệt yêu cầu của Admin kia (không tự duyệt)
        seedAdmin(adminRole, secondaryAdminEmail, adminFullName + " 2");
    }

    private void seedAdmin(Role adminRole, String rawEmail, String fullName) {
        if (adminPassword == null || adminPassword.isBlank() || rawEmail == null || rawEmail.isBlank()) {
            return;
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepo.existsByEmail(email)) {
            return;
        }
        userRepo.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .fullName(fullName)
                .role(adminRole)
                .build());
        log.info("Đã tạo tài khoản admin mặc định: {}", email);
    }
}
