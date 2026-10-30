package com.tanle.meeting_room_booking.common.idempotency;

import com.tanle.meeting_room_booking.common.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Hỗ trợ header Idempotency-Key cho các thao tác POST quan trọng theo Spec mục 15, 23.4 & AC-30:
 * - Cùng người dùng + cùng thao tác + cùng Idempotency-Key + cùng nội dung body trong 24 giờ -> trả lại nguyên kết quả đã lưu mà không tạo thêm dữ liệu/sự kiện.
 * - Cùng Idempotency-Key nhưng khác nội dung body -> trả 409 IDEMPOTENCY_KEY_REUSED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Idempotency-Key";
    private static final long RETENTION_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String key = request.getHeader(HEADER_NAME);
        if (key == null || key.isBlank()) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/auth/") || uri.endsWith("/preview");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser authUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(HEADER_NAME).trim();
        if (rawKey.length() > 128) {
            writeConflictOrBadRequest(response, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR", "Header Idempotency-Key tối đa 128 ký tự");
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256Hex(wrappedRequest.getCachedBody());
        String operationKey = request.getMethod().toUpperCase() + " " + request.getRequestURI();
        OffsetDateTime now = OffsetDateTime.now();

        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findValidRecord(
                authUser.id(), operationKey, rawKey, now
        );

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                writeConflictOrBadRequest(
                        response,
                        HttpServletResponse.SC_CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác"
                );
                return;
            }

            response.setStatus(existing.getResponseStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("X-Idempotent-Replay", "true");
            response.getWriter().write(existing.getResponseBody());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(wrappedRequest, wrappedResponse);

        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            try {
                idempotencyRepository.deleteExpiredRecords(now);
                idempotencyRepository.saveAndFlush(IdempotencyRecord.builder()
                        .userId(authUser.id())
                        .operationKey(operationKey)
                        .idempotencyKey(rawKey)
                        .requestHash(requestHash)
                        .responseStatus(status)
                        .responseBody(responseBody)
                        .expiresAt(now.plusHours(RETENTION_HOURS))
                        .build());
            } catch (Exception ex) {
                log.warn("[IdempotencyFilter] Không thể lưu bản ghi Idempotency-Key '{}': {}", rawKey, ex.getMessage());
            }
        }

        wrappedResponse.copyBodyToResponse();
    }

    private void writeConflictOrBadRequest(
            HttpServletResponse response,
            int httpStatus,
            String code,
            String message
    ) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = String.format(
                "{\"status\":%d,\"code\":\"%s\",\"errorCode\":\"%s\",\"title\":\"%s\",\"detail\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                httpStatus, code, code, code, message, message, UUID.randomUUID()
        );
        response.getWriter().write(json);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}

