package com.tanle.meeting_room_booking.common.config;

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

import java.util.Locale;
import java.util.Map;

/**
 * Seeds the base roles and (optionally) the first admin account. Idempotent.
 * Without roles no user can be created, because users.role_id is NOT NULL.
 * The admin is created only when app.admin.password (env ADMIN_PASSWORD) is set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Map<String, String> DEFAULT_ROLES = Map.of(
            "ADMIN", "Quản trị hệ thống",
            "USER", "Người dùng thông thường"
    );

    private final RoleRepository roleRepository;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@meetingroom.local}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:System Admin}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(String... args) {
        DEFAULT_ROLES.forEach((name, description) -> {
            if (!roleRepository.existsByName(name)) {
                roleRepository.save(Role.builder().name(name).description(description).build());
            }
        });
        seedAdmin();
    }

    private void seedAdmin() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_PASSWORD chưa được cấu hình, bỏ qua việc tạo tài khoản admin mặc định");
            return;
        }
        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepo.existsByEmail(email)) {
            return;
        }
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        userRepo.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName)
                .role(adminRole)
                .build());
        log.info("Đã tạo tài khoản admin mặc định: {}", email);
    }
}
