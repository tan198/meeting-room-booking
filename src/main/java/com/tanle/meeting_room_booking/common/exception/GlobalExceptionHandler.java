package com.tanle.meeting_room_booking.common.exception;


import com.tanle.meeting_room_booking.common.logging.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("[BusinessException] Code: {} | Message: {}", errorCode.name(), ex.getMessage());

        ProblemDetail problem = buildProblemDetail(errorCode.getStatus(), errorCode.name(), ex.getMessage(),request);
        if(ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            problem.setProperty("details", ex.getDetails());
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("[ValidateException] Fields: {}", fieldErrors);
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        String rootMgs = ex.getMostSpecificCause().getMessage();
        log.warn("[DB Constraint Violation] Message: {}", rootMgs);

        if (rootMgs != null && !rootMgs.isEmpty()) {
            if (rootMgs.contains("excl_bookings_no_room_overlap")){
                return buildProblemDetail(
                        HttpStatus.CONFLICT,
                        ErrorCode.ROOM_TIME_CONFLICT.name(),
                        "Phòng họp vừa được người khác đặt trong khung giờ này (Bắt bởi DB Exclusion Constraint).",
                        request
                );
            }
            if(rootMgs.contains("uq_user_email")){
                return buildProblemDetail(
                        HttpStatus.CONFLICT,
                        ErrorCode.DUPLICATE_RESOURCE.name(),
                        "Email này đã tồn tại trong hệ thống.",
                        request
                );
            }
            if(rootMgs.contains("uq_room_name")){
                return buildProblemDetail(
                        HttpStatus.CONFLICT,
                        ErrorCode.DUPLICATE_RESOURCE.name(),
                        "Phong họp này đã tồn tại.",
                        request
                );
            }
        }
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_RESOURCE.name(),
                "Vi phạm ràng buộc toàn vẹn dữ liệu.",
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("[OptimisticLockingFailure] Xung đột cập nhật đồng thời: {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION.name(),
                ErrorCode.CONCURRENT_MODIFICATION.getDefaultMessage(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public  ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN.name(),
                ErrorCode.FORBIDDEN.getDefaultMessage(),
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.name(),
               ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("[Exception] Message: {}", request.getRequestURI(), ex);
        return buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.name(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request
        );
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(errorCode);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", MDC.get(RequestLoggingFilter.TRACE_ID_KEY));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }
}
