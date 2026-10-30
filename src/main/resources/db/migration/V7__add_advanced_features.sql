-- =====================================================================
-- V7: Tính năng nâng cao (P2) - UUID Edition
--   - meeting_mode: STANDARD | SEQUENTIAL_SLOTS (phiên phỏng vấn / 1-1 của HR)
--   - is_confidential: họp kín (ẩn tiêu đề với người ngoài)
--   - parent_booking_id: truy vết chuỗi gia hạn / chuyển phòng (relocate-extend)
--   - booking_guests: khách ngoài công ty
--   - booking_slots: hàng đợi các lượt trong 1 booking
--   - booking_audit_logs: dấu vết thao tác (Admin Override, extend, relocate...)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Mở rộng bảng bookings
-- ---------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN meeting_mode      VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN is_confidential   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN parent_booking_id UUID        REFERENCES bookings (id),
    ADD CONSTRAINT chk_bookings_mode
        CHECK (meeting_mode IN ('STANDARD', 'SEQUENTIAL_SLOTS')),
    ADD CONSTRAINT chk_bookings_parent
        CHECK (parent_booking_id IS NULL OR parent_booking_id <> id);

CREATE INDEX idx_bookings_parent ON bookings (parent_booking_id) WHERE parent_booking_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. BOOKING_GUESTS - khách ngoài công ty (không có tài khoản)
-- ---------------------------------------------------------------------
CREATE TABLE booking_guests (
    id           UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id   UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    full_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(255),
    organization VARCHAR(150),

    CONSTRAINT chk_guests_email_lc CHECK (email IS NULL OR email = lower(email)),
    CONSTRAINT uq_guests_booking_email UNIQUE (booking_id, email)
);

CREATE INDEX idx_guests_booking ON booking_guests (booking_id);

-- ---------------------------------------------------------------------
-- 3. BOOKING_SLOTS - các lượt tuần tự trong 1 booking
-- Mỗi slot thuộc về ĐÚNG MỘT trong hai: nhân viên nội bộ HOẶC khách ngoài.
-- ---------------------------------------------------------------------
CREATE TABLE booking_slots (
    id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id        UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    user_id           UUID        REFERENCES users (id),
    guest_id          UUID        REFERENCES booking_guests (id) ON DELETE CASCADE,
    slot_order        INT         NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    actual_start_time TIMESTAMPTZ,
    actual_end_time   TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_slots_owner  CHECK (num_nonnulls(user_id, guest_id) = 1),
    CONSTRAINT chk_slots_time   CHECK (start_time < end_time),
    CONSTRAINT chk_slots_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'DONE', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT chk_slots_actual CHECK (actual_end_time IS NULL
                                       OR (actual_start_time IS NOT NULL AND actual_end_time >= actual_start_time)),

    CONSTRAINT uq_slots_order UNIQUE (booking_id, slot_order),
    CONSTRAINT uq_slots_user  UNIQUE (booking_id, user_id),
    CONSTRAINT uq_slots_guest UNIQUE (booking_id, guest_id),

    CONSTRAINT excl_slots_no_overlap EXCLUDE USING gist (
        booking_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('WAITING', 'IN_PROGRESS')) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_slots_user          ON booking_slots (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_slots_waiting_start ON booking_slots (start_time) WHERE status = 'WAITING';

-- ---------------------------------------------------------------------
-- 4. BOOKING_AUDIT_LOGS - dấu vết thao tác nhạy cảm
-- ---------------------------------------------------------------------
CREATE TABLE booking_audit_logs (
    id         UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id UUID         NOT NULL REFERENCES bookings (id),
    actor_id   UUID         NOT NULL REFERENCES users (id),
    action     VARCHAR(40)  NOT NULL,
    reason     VARCHAR(255),
    details    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_booking ON booking_audit_logs (booking_id, created_at DESC);
CREATE INDEX idx_audit_actor   ON booking_audit_logs (actor_id, created_at DESC);
