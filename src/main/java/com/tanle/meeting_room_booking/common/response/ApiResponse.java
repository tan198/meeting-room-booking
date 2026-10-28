package com.tanle.meeting_room_booking.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tanle.meeting_room_booking.common.logging.RequestLoggingFilter;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        String traceId,
        T data,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return ok("Success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                MDC.get(RequestLoggingFilter.TRACE_ID_KEY),
                data,
                OffsetDateTime.now()
        );
    }

    public static ApiResponse<Void> ok(String message) {
        return ok(message, null);
    }
}
