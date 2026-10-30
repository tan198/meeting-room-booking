-- =====================================================================
-- V11: Nâng cấp theo chuẩn nghiệm thu meeting-room-spec.md (Phase 1):
--   1. Hỗ trợ yêu cầu đặt phòng ở trạng thái PENDING (chưa bắt buộc gán phòng chính thức room_id ngay lúc gửi,
--      có preferred_room_id), Admin khác người tổ chức phê duyệt (APPROVED), từ chối (REJECTED),
--      hoặc tự hết hạn (EXPIRED) khi tới giờ bắt đầu mà chưa duyệt.
--   2. Bổ sung meeting_type (GENERAL, SYNC, PLANNING, REVIEW, SALARY_REVIEW, PERFORMANCE_REVIEW,
--      INTERVIEW, TRAINING, OTHER) và visibility (STANDARD, CONFIDENTIAL).
--   3. Tách trạng thái sử dụng thực tế (usage_status: NOT_APPLICABLE, NOT_CHECKED_IN, IN_USE,
--      COMPLETED, NO_SHOW, INTERRUPTED), check_in_deadline, checked_in_by, checked_out_by,
--      evidence_type, end_source, released_at, cancellation_reason_code.
--   4. Bổ sung related_department_ids, organizer_department_id, attendee_count, note.
-- =====================================================================

-- 1. Cho phép room_id NULL khi booking đang ở trạng thái PENDING hoặc REJECTED / EXPIRED
ALTER TABLE bookings
    ALTER COLUMN room_id DROP NOT NULL;

-- 2. Bổ sung các cột phục vụ quy trình Phê duyệt, Phân loại MeetingType/Visibility & Theo dõi sử dụng (Usage)
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS preferred_room_id        UUID        REFERENCES rooms (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS organizer_department_id  UUID        REFERENCES work_groups (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS related_department_ids   JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS meeting_type             VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS visibility               VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS attendee_count           INT         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS note                     TEXT,
    ADD COLUMN IF NOT EXISTS approved_by              UUID        REFERENCES users (id),
    ADD COLUMN IF NOT EXISTS approved_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approval_note            TEXT,
    ADD COLUMN IF NOT EXISTS rejected_by              UUID        REFERENCES users (id),
    ADD COLUMN IF NOT EXISTS rejected_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reject_reason            TEXT,
    ADD COLUMN IF NOT EXISTS cancellation_reason_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS usage_status             VARCHAR(25) NOT NULL DEFAULT 'NOT_CHECKED_IN',
    ADD COLUMN IF NOT EXISTS check_in_deadline        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS checked_in_by            UUID        REFERENCES users (id),
    ADD COLUMN IF NOT EXISTS checked_out_by           UUID        REFERENCES users (id),
    ADD COLUMN IF NOT EXISTS evidence_type            VARCHAR(30),
    ADD COLUMN IF NOT EXISTS end_source               VARCHAR(30),
    ADD COLUMN IF NOT EXISTS released_at              TIMESTAMPTZ;

-- 3. Cập nhật dữ liệu cũ để đồng bộ với cột mới
UPDATE bookings
SET visibility = CASE WHEN is_confidential THEN 'CONFIDENTIAL' ELSE 'STANDARD' END,
    usage_status = CASE
        WHEN status = 'IN_PROGRESS' THEN 'IN_USE'
        WHEN status = 'COMPLETED'   THEN 'COMPLETED'
        WHEN status = 'NO_SHOW'     THEN 'NO_SHOW'
        WHEN status = 'CANCELLED'   THEN 'NOT_APPLICABLE'
        ELSE 'NOT_CHECKED_IN'
    END,
    check_in_deadline = COALESCE(check_in_deadline, LEAST(start_time + INTERVAL '10 minutes', end_time));

-- 4. Nâng cấp các CHECK constraint trên bảng bookings để hỗ trợ cả trạng thái Spec (PENDING, APPROVED, REJECTED, EXPIRED)
--    lẫn trạng thái hiện hữu (CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW)
ALTER TABLE bookings
    DROP CONSTRAINT IF EXISTS chk_bookings_status,
    DROP CONSTRAINT IF EXISTS chk_bookings_cancel_audit,
    DROP CONSTRAINT IF EXISTS chk_bookings_in_progress,
    DROP CONSTRAINT IF EXISTS chk_bookings_completed,
    DROP CONSTRAINT IF EXISTS excl_bookings_no_room_overlap;

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_status CHECK (
        status IN (
            'PENDING',
            'APPROVED',
            'REJECTED',
            'EXPIRED',
            'CONFIRMED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED',
            'NO_SHOW'
        )
    ),
    ADD CONSTRAINT chk_bookings_meeting_type CHECK (
        meeting_type IN (
            'GENERAL',
            'SYNC',
            'PLANNING',
            'REVIEW',
            'SALARY_REVIEW',
            'PERFORMANCE_REVIEW',
            'INTERVIEW',
            'TRAINING',
            'OTHER'
        )
    ),
    ADD CONSTRAINT chk_bookings_visibility CHECK (
        visibility IN ('STANDARD', 'CONFIDENTIAL')
    ),
    ADD CONSTRAINT chk_bookings_usage_status CHECK (
        usage_status IN (
            'NOT_APPLICABLE',
            'NOT_CHECKED_IN',
            'IN_USE',
            'COMPLETED',
            'NO_SHOW',
            'INTERRUPTED'
        )
    ),
    -- Ràng buộc loại trừ vật lý chống đặt trùng phòng:
    -- Chỉ áp dụng khi đã gán phòng (room_id IS NOT NULL) và lịch đang giữ phòng hiệu lực
    -- (APPROVED / CONFIRMED / IN_PROGRESS và chưa bị giải phóng hoàn toàn).
    -- Sử dụng LEAST(end_time, COALESCE(released_at, end_time)) để khi check-out sớm sẽ tự giải phóng phần thời gian còn lại
    -- mà vẫn giữ nguyên end_time gốc theo đúng Spec 25.4 & 25.5!
    ADD CONSTRAINT excl_bookings_no_room_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, LEAST(end_time, COALESCE(released_at, end_time)), '[)') WITH &&
    ) WHERE (
        deleted_at IS NULL
        AND room_id IS NOT NULL
        AND status IN ('APPROVED', 'CONFIRMED', 'IN_PROGRESS')
        AND usage_status NOT IN ('NO_SHOW', 'INTERRUPTED')
        AND (released_at IS NULL OR released_at > start_time)
    );

CREATE INDEX IF NOT EXISTS idx_bookings_pending_start
    ON bookings (start_time)
    WHERE deleted_at IS NULL AND status = 'PENDING';

