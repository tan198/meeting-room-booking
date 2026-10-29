package com.tanle.meeting_room_booking.auth.service;

import com.tanle.meeting_room_booking.auth.dto.LoginRequest;
import com.tanle.meeting_room_booking.auth.dto.LoginResponse;
import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.common.security.JwtTokenProvider;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    private final String dummyHash;

    public AuthService(UserRepo userRepo,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public LoginResponse login(LoginRequest rq) {
        String email = rq.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findWithRoleByEmail(email).orElse(null);

        String hash = user != null ? user.getPassword() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rq.password(), hash);

        if (user == null || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse refresh(String refreshToken) {
        UUID ownerId = refreshTokenService.findOwnerIdOrThrow(refreshToken);

        // Khóa bi quan (SELECT ... FOR UPDATE) trên dòng User trước khi consume & cấp token mới
        // để tuần tự hóa hoàn toàn với logoutAll / revokeAll, ngăn token mới sống sót sau khi logoutAll.
        User user = userRepo.findByIdForUpdate(ownerId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        refreshTokenService.consume(refreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        userRepo.findByIdForUpdate(userId);
        refreshTokenService.revokeAll(userId);
    }

    private LoginResponse issueTokens(User user) {
        var access = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().getName());
        var refresh = refreshTokenService.issue(user.getId());
        return new LoginResponse(
                access.token(),
                refresh.token()
        );
    }
}
