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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String traceId = Optional.ofNullable(request.getHeader(TRACE_HEADER)).filter(id -> !id.isBlank()).orElseGet(() -> UUID.randomUUID().toString().substring(0,8));
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_KEY, traceId);

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = request.getRequestURI();

        try {
            log.info("--> [START] {} {}", method, fullPath);
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException |  RuntimeException exception) {
          log.error("<-- [FAILED] {} {}", method, fullPath, exception);
        } finally {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            int getStatusCode = response.getStatus();

            if (getStatusCode >= 500){
               log.error("<-- [END] {} {} - Status:{} ({} ms)", method, fullPath, getStatusCode, duration);
            } else if (getStatusCode >= 400 && getStatusCode <= 499) {
                log.warn("<-- [END] {} {} - Status:{} ({} ms)", method, fullPath, getStatusCode, duration);
            } else {
                log.info("<-- [END] {} {} -Status: {} ({} ms)", method, fullPath,  getStatusCode, duration);
            }
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
