package com.tanle.meeting_room_booking.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String traceId = Optional.ofNullable(request.getHeader(TRACE_HEADER))
                .map(String::trim)
                .filter(id -> SAFE_TRACE_ID.matcher(id).matches())
                .orElseGet(() -> UUID.randomUUID().toString().substring(0, 8));
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long startTime = System.nanoTime();
        String method = request.getMethod();
        String fullPath = request.getRequestURI();

        try {
            log.info("--> [START] {} {}", method, fullPath);
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            log.error("<-- [FAILED] {} {}", method, fullPath, exception);
            throw exception;
        } finally {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            int getStatusCode = response.getStatus();

            if (getStatusCode >= 500) {
                log.error("<-- [END] {} {} - Status:{} ({} ms)", method, fullPath, getStatusCode, duration);
            } else if (getStatusCode >= 400 && getStatusCode <= 499) {
                log.warn("<-- [END] {} {} - Status:{} ({} ms)", method, fullPath, getStatusCode, duration);
            } else {
                log.info("<-- [END] {} {} - Status:{} ({} ms)", method, fullPath, getStatusCode, duration);
            }
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
