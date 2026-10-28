package com.tanle.meeting_room_booking.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu phải nhỏ hơn thời gian kết thúc và ở tương lai"),
    ROOM_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Số lượng người tham gia vượt quá sức chứa của phòng"),
    ROOM_NOT_AVAILABLE_STATUS(HttpStatus.BAD_REQUEST, "Phòng họp hiện đang bảo trì hoặc ngưng hoạt động"),
    INVALID_BOOKING_STATE(HttpStatus.BAD_REQUEST, "Trạng thái lịch đặt phòng không hợp lệ cho thao tác này"),

    // 401 & 403 Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc phiên đăng nhập đã hết hạn"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phòng họp"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt phòng"),

    // 409 Conflict (Cực kỳ quan trọng cho bài toán Đặt phòng)
    ROOM_TIME_CONFLICT(HttpStatus.CONFLICT, "Phòng họp đã có người đặt trong khung giờ này"),
    ATTENDEE_TIME_CONFLICT(HttpStatus.CONFLICT, "Thành viên tham gia bị trùng lịch với một cuộc họp khác"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Dữ liệu đã tồn tại trong hệ thống"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu vừa được cập nhật bởi một thao tác khác, vui lòng thử lại"),

    // 500 Internal Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống nội bộ");

    private final HttpStatus status;
    private final String defaultMessage;
}
