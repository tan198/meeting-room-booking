-- =====================================================================
-- V10: Nâng cấp bảo toàn dữ liệu (Data Integrity) & mở rộng giới hạn Notification:
--   1. Backfill toàn bộ organizer của các booking cũ chưa có dòng tương ứng
--      trong bảng booking_attendees (đảm bảo "Lịch của tôi" không sót dữ liệu cũ).
--   2. Mở rộng độ dài cột notifications.title từ VARCHAR(200) lên VARCHAR(300)
--      để tiêu đề thông báo kèm tiền tố ("Lời mời tham dự cuộc họp: ...")
--      không bị lỗi vượt độ dài khi booking.title dài tới 200 ký tự.
-- =====================================================================

-- 1. Backfill organizer vào booking_attendees cho các booking cũ
INSERT INTO booking_attendees (booking_id, user_id, attendee_role, rsvp_status, responded_at, created_at)
SELECT b.id, b.organizer_id, 'ORGANIZER', 'ACCEPTED', b.created_at, b.created_at
FROM bookings b
WHERE NOT EXISTS (
    SELECT 1
    FROM booking_attendees ba
    WHERE ba.booking_id = b.id
      AND ba.user_id = b.organizer_id
);

-- 2. Mở rộng cột title của notifications lên VARCHAR(300)
ALTER TABLE notifications
    ALTER COLUMN title TYPE VARCHAR(300);

