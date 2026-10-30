-- =====================================================================
-- V12: Nâng cấp theo chuẩn nghiệm thu meeting-room-spec.md (Phase 2):
--   1. Bảng booking_changes: Đề nghị thay đổi lịch đã duyệt (APPROVED) mà không làm
--      mất phòng cũ trong khi chờ Admin phê duyệt (Spec mục 9, AC-14..AC-17).
--   2. Bảng room_issues & room_issue_histories: Báo cáo sự cố phòng họp
--      (EQUIPMENT, POWER, CLEANLINESS, OTHER) và luồng xử lý trạng thái
--      OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (Spec mục 10, AC-20, AC-25).
--   3. Bảng room_blocks: Khoảng chặn sử dụng phòng theo thời gian (ACTIVE / CANCELLED)
--      với GiST Exclusion Constraint chống giao nhau giữa 2 khoảng chặn ACTIVE
--      cùng phòng (Spec mục 11, AC-21..AC-25).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. BOOKING_CHANGES (Đề nghị thay đổi lịch đã duyệt - Spec mục 9)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_changes (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id              UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    base_version            BIGINT      NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    purpose                 TEXT,
    note                    TEXT,
    meeting_type            VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    visibility              VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    start_time              TIMESTAMPTZ NOT NULL,
    end_time                TIMESTAMPTZ NOT NULL,
    attendee_count          INT         NOT NULL DEFAULT 1,
    preferred_room_id       UUID        REFERENCES rooms (id) ON DELETE SET NULL,
    team_id                 UUID        REFERENCES teams (id) ON DELETE SET NULL,
    group_id                UUID        REFERENCES work_groups (id) ON DELETE SET NULL,
    related_department_ids  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    participant_ids         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    assigned_room_id        UUID        REFERENCES rooms (id) ON DELETE SET NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_by            UUID        REFERENCES users (id),
    processed_at            TIMESTAMPTZ,
    process_reason          TEXT,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_booking_changes_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT chk_booking_changes_time CHECK (start_time < end_time),
    CONSTRAINT chk_booking_changes_visibility CHECK (visibility IN ('STANDARD', 'CONFIDENTIAL'))
);

-- Mỗi booking chỉ có tối đa một đề nghị thay đổi đang ở trạng thái PENDING (Spec 9 & 23.4: PENDING_CHANGE_EXISTS)
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_changes_one_pending
    ON booking_changes (booking_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_booking_changes_booking
    ON booking_changes (booking_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_booking_changes_pending_start
    ON booking_changes (start_time)
    WHERE status = 'PENDING';


-- ---------------------------------------------------------------------
-- 2. ROOM_ISSUES & ROOM_ISSUE_HISTORIES (Báo cáo sự cố - Spec mục 10)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_issues (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    room_id             UUID         NOT NULL REFERENCES rooms (id),
    reporter_id         UUID         NOT NULL REFERENCES users (id),
    related_booking_id  UUID         REFERENCES bookings (id) ON DELETE SET NULL,
    category            VARCHAR(30)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    proposed_impact     VARCHAR(20)  NOT NULL,
    actual_impact       VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolution          TEXT,
    processed_by        UUID         REFERENCES users (id),
    processed_at        TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_room_issues_category CHECK (
        category IN ('EQUIPMENT', 'POWER', 'CLEANLINESS', 'OTHER')
    ),
    CONSTRAINT chk_room_issues_proposed_impact CHECK (
        proposed_impact IN ('MINOR', 'DEGRADED', 'UNUSABLE')
    ),
    CONSTRAINT chk_room_issues_actual_impact CHECK (
        actual_impact IS NULL OR actual_impact IN ('MINOR', 'DEGRADED', 'UNUSABLE')
    ),
    CONSTRAINT chk_room_issues_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')
    )
);

CREATE INDEX IF NOT EXISTS idx_room_issues_room ON room_issues (room_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_room_issues_reporter ON room_issues (reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_room_issues_status ON room_issues (status, created_at DESC);

CREATE TABLE IF NOT EXISTS room_issue_histories (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id    UUID        NOT NULL REFERENCES room_issues (id) ON DELETE CASCADE,
    actor_id    UUID        NOT NULL REFERENCES users (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      TEXT,
    resolution  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_issue_histories_issue
    ON room_issue_histories (issue_id, created_at DESC);


-- ---------------------------------------------------------------------
-- 3. ROOM_BLOCKS (Khoảng chặn phòng theo thời gian - Spec mục 11)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_blocks (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    room_id         UUID        NOT NULL REFERENCES rooms (id),
    issue_id        UUID        REFERENCES room_issues (id) ON DELETE SET NULL,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    reason          TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      UUID        NOT NULL REFERENCES users (id),
    cancelled_by    UUID        REFERENCES users (id),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    replaced_by_id  UUID        REFERENCES room_blocks (id) ON DELETE SET NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_room_blocks_time CHECK (start_time < end_time),
    CONSTRAINT chk_room_blocks_status CHECK (status IN ('ACTIVE', 'CANCELLED')),

    -- Hai RoomBlock ACTIVE cùng phòng không được giao nhau; cho phép hai khoảng liền kề [start, end) (Spec 11.1)
    CONSTRAINT excl_room_blocks_no_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE INDEX IF NOT EXISTS idx_room_blocks_room_time
    ON room_blocks (room_id, start_time, end_time)
    WHERE status = 'ACTIVE';

