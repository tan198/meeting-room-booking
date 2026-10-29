package com.tanle.meeting_room_booking.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    FIELD_NOT_WRITABLE(HttpStatus.BAD_REQUEST, "Trường dữ liệu không được phép chỉnh sửa trực tiếp"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu phải nhỏ hơn thời gian kết thúc, đúng độ chính xác đến phút và ở tương lai"),
    VISIBILITY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Loại cuộc họp nhạy cảm bắt buộc phải có mức riêng tư CONFIDENTIAL và không được hạ về STANDARD"),
    ROOM_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Số lượng người tham gia vượt quá sức chứa của phòng"),
    ROOM_NOT_AVAILABLE_STATUS(HttpStatus.BAD_REQUEST, "Phòng họp hiện đang bảo trì hoặc ngưng hoạt động"),
    INVALID_BOOKING_STATE(HttpStatus.BAD_REQUEST, "Trạng thái lịch đặt phòng không hợp lệ cho thao tác này"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu không khớp"),
    SYSTEM_ROLE_PROTECTED(HttpStatus.BAD_REQUEST, "Không thể xóa hoặc sửa vai trò mặc định của hệ thống"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "Không thể xóa vai trò đang được gán cho người dùng"),

    // 401 & 403 Auth & Permissions
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc phiên đăng nhập đã hết hạn"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    SELF_APPROVAL_FORBIDDEN(HttpStatus.FORBIDDEN, "Quản trị viên không được tự phê duyệt hoặc từ chối yêu cầu đặt phòng của chính mình"),
    TEAM_MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "Người tổ chức phải là thành viên đang hoạt động của Team được chọn"),
    PROJECT_MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "Người tổ chức phải là thành viên đang hoạt động của Dự án được chọn"),
    CHECK_OUT_FORBIDDEN(HttpStatus.FORBIDDEN, "Chỉ người tổ chức, người đã check-in hoặc quản trị viên mới có quyền check-out"),

    // 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phòng họp"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt phòng"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy vai trò"),
    PERMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy quyền hạn"),
    CANNOT_MODIFY_SELF(HttpStatus.FORBIDDEN, "Không thể thực hiện thao tác này"),

    // 409 Conflict
    ROOM_TIME_CONFLICT(HttpStatus.CONFLICT, "Phòng họp đã có người đặt trong khung giờ này"),
    ROOM_UNAVAILABLE(HttpStatus.CONFLICT, "Phòng họp không còn trống trong khung giờ yêu cầu"),
    ROOM_REQUIREMENTS_NOT_MET(HttpStatus.CONFLICT, "Phòng họp không đáp ứng yêu cầu về sức chứa hoặc thiết bị"),
    ATTENDEE_TIME_CONFLICT(HttpStatus.CONFLICT, "Thành viên tham gia bị trùng lịch với một cuộc họp khác"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Dữ liệu đã tồn tại trong hệ thống"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu vừa được cập nhật bởi một thao tác khác, vui lòng tải lại trước khi xử lý"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Yêu cầu đã thay đổi phiên bản. Vui lòng tải lại trước khi xử lý"),
    CHECK_IN_TOO_EARLY(HttpStatus.CONFLICT, "Chưa đến giờ bắt đầu cuộc họp, không thể check-in sớm"),
    CHECK_IN_DEADLINE_PASSED(HttpStatus.CONFLICT, "Đã quá thời hạn check-in (10 phút sau giờ bắt đầu), cuộc họp đã tự hủy NO_SHOW"),
    USAGE_NOT_ACTIVE(HttpStatus.CONFLICT, "Cuộc họp hiện không ở trạng thái đang sử dụng (IN_USE)"),
    USAGE_ALREADY_FINISHED(HttpStatus.CONFLICT, "Phiên sử dụng phòng đã kết thúc"),
    REFERENCE_NO_LONGER_VALID(HttpStatus.CONFLICT, "Người tổ chức, thành viên, Team hoặc Dự án liên kết không còn hợp lệ"),

    // 500 Internal Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống nội bộ");

    private final HttpStatus status;
    private final String defaultMessage;
}
