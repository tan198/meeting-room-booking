package com.tanle.meeting_room_booking.booking.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Danh mục loại cuộc họp theo Spec mục 27.4.
 * Các loại nhạy cảm (SALARY_REVIEW, PERFORMANCE_REVIEW, INTERVIEW) bắt buộc phải có visibility = CONFIDENTIAL (POL-23).
 */
@Getter
@RequiredArgsConstructor
public enum MeetingType {
    GENERAL("Họp thông thường", false),
    SYNC("Đồng bộ công việc team/bộ phận", false),
    PLANNING("Lập kế hoạch, phân công", false),
    REVIEW("Review tiến độ, sản phẩm hoặc kết quả công việc chung", false),
    SALARY_REVIEW("Trao đổi/review lương", true),
    PERFORMANCE_REVIEW("Đánh giá cá nhân, trao đổi nhân sự", true),
    INTERVIEW("Phỏng vấn ứng viên", true),
    TRAINING("Đào tạo, chia sẻ nội bộ", false),
    OTHER("Mục đích khác", false);

    private final String description;
    private final boolean mandatoryConfidential;
}

