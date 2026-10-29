package com.tanle.meeting_room_booking.common.security;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepo userRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();

            jwtTokenProvider.validateAndGetUserId(token)
                    .flatMap(userRepo::findWithRoleById)
                    .filter(user -> user.isActive() && !user.isDeleted())
                    .ifPresent(user -> {
                        Set<String> permissionCodes = user.getRole().getPermissions().stream()
                                .map(Permission::getCode)
                                .collect(Collectors.toUnmodifiableSet());

                        List<GrantedAuthority> authorities = new ArrayList<>(permissionCodes.size() + 1);
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
                        permissionCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

                        AuthUser principal = new AuthUser(
                                user.getId(),
                                user.getEmail(),
                                user.getRole().getName(),
                                permissionCodes
                        );
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
