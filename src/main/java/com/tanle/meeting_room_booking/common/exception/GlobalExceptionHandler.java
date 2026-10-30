package com.tanle.meeting_room_booking.common.exception;

import com.tanle.meeting_room_booking.common.logging.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("[BusinessException] Code: {} | Message: {}", errorCode.name(), ex.getMessage());

        ProblemDetail problem = buildProblemDetail(errorCode.getStatus(), errorCode.name(), ex.getMessage(), request);
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
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
        String constraintName = extractConstraintName(ex);
        String rootMsg = ex.getMostSpecificCause().getMessage();
        log.warn("[DB Constraint Violation] Constraint: {} | Message: {}", constraintName, rootMsg);

        String target = constraintName != null ? constraintName : (rootMsg != null ? rootMsg : "");

        if (target.contains("excl_bookings_no_room_overlap") || target.contains("excl_room_blocks_no_overlap")) {
            return buildProblemDetail(
                    HttpStatus.CONFLICT,
                    ErrorCode.ROOM_UNAVAILABLE.name(),
                    "Phòng họp hoặc khoảng chặn bị giao lịch trong khung giờ này (Bắt bởi DB Exclusion Constraint).",
                    request
            );
        }
        if (target.contains("uq_booking_changes_one_pending")) {
            return buildProblemDetail(
                    HttpStatus.CONFLICT,
                    ErrorCode.PENDING_CHANGE_EXISTS.name(),
                    ErrorCode.PENDING_CHANGE_EXISTS.getDefaultMessage(),
                    request
            );
        }
        if (target.contains("uq_users_email")) {
            return buildProblemDetail(
                    HttpStatus.CONFLICT,
                    ErrorCode.DUPLICATE_RESOURCE.name(),
                    "Email này đã tồn tại trong hệ thống.",
                    request
            );
        }
        if (target.contains("uq_rooms_name")) {
            return buildProblemDetail(
                    HttpStatus.CONFLICT,
                    ErrorCode.DUPLICATE_RESOURCE.name(),
                    "Phòng họp này đã tồn tại.",
                    request
            );
        }
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_RESOURCE.name(),
                "Vi phạm ràng buộc toàn vẹn dữ liệu.",
                request
        );
    }

    private String extractConstraintName(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName().toLowerCase(Locale.ROOT);
            }
            cause = cause.getCause();
        }
        return null;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("[OptimisticLockingFailure] Xung đột cập nhật đồng thời: {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                ErrorCode.VERSION_CONFLICT.name(),
                ErrorCode.VERSION_CONFLICT.getDefaultMessage(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("[BadRequest] {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR.name(),
                "Request không hợp lệ hoặc sai định dạng dữ liệu.",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.name(),
                ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(),
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
        String traceId = MDC.get(RequestLoggingFilter.TRACE_ID_KEY);
        problem.setProperty("code", errorCode);
        problem.setProperty("message", detail);
        problem.setProperty("requestId", traceId);
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }
}
