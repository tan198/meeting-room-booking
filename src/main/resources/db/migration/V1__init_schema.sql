
-- =====================================================================
-- V1: Schema lõi (P0) - users, rooms, bookings (UUID v4 Edition)
-- Soft delete: deleted_at IS NULL = active, otherwise deleted
-- =====================================================================

-- btree_gist cho phép kết hợp "=" (room_id UUID) và "&&" (range)
-- trong một GiST index/exclusion constraint.
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- ---------------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------------

CREATE TABLE users (
                       id            UUID         NOT NULL DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(100) NOT NULL,
                       role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
                       is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       deleted_at    TIMESTAMPTZ,

                       CONSTRAINT pk_users PRIMARY KEY (id),

    -- Ép email lưu chữ thường.
                       CONSTRAINT chk_users_email_lc
                           CHECK (email = LOWER(email)),

                       CONSTRAINT chk_users_role
                           CHECK (role IN ('USER', 'ADMIN'))
);

-- Chỉ email của user chưa bị xóa mềm mới cần duy nhất.
-- Email của user đã xóa mềm có thể được tái sử dụng.
CREATE UNIQUE INDEX uq_users_email
    ON users (email)
    WHERE deleted_at IS NULL;


-- ---------------------------------------------------------------------
-- 2. ROOMS
-- ---------------------------------------------------------------------

CREATE TABLE rooms (
                       id         UUID         NOT NULL DEFAULT gen_random_uuid(),
                       name       VARCHAR(100) NOT NULL,
                       floor      INT          NOT NULL,
                       capacity   INT          NOT NULL,
                       facilities JSONB        NOT NULL DEFAULT '[]'::jsonb,
                       status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

                       created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       deleted_at TIMESTAMPTZ,

                       CONSTRAINT pk_rooms PRIMARY KEY (id),

                       CONSTRAINT chk_rooms_capacity
                           CHECK (capacity > 0),

                       CONSTRAINT chk_rooms_status
                           CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE')),

                       CONSTRAINT chk_rooms_facilities
                           CHECK (JSONB_TYPEOF(facilities) = 'array')
);

-- Chỉ tên phòng của phòng chưa bị xóa mềm mới cần duy nhất.
CREATE UNIQUE INDEX uq_rooms_name
    ON rooms (name)
    WHERE deleted_at IS NULL;


-- ---------------------------------------------------------------------
-- 3. BOOKINGS
-- ---------------------------------------------------------------------

CREATE TABLE bookings (
                          id                UUID         NOT NULL DEFAULT gen_random_uuid(),

                          room_id           UUID         NOT NULL,
                          organizer_id      UUID         NOT NULL,

                          title             VARCHAR(200) NOT NULL,
                          description       TEXT,

    -- Khung giờ đặt theo kế hoạch.
                          start_time        TIMESTAMPTZ  NOT NULL,
                          end_time          TIMESTAMPTZ  NOT NULL,

    -- Thời gian thực tế khi check-in/check-out.
                          actual_start_time TIMESTAMPTZ,
                          actual_end_time   TIMESTAMPTZ,

                          status            VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',

    -- Tránh scheduler gửi thông báo lặp lại.
                          pre_end_notified  BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Audit thông tin hủy booking.
                          cancelled_by      UUID,
                          cancelled_at      TIMESTAMPTZ,
                          cancel_reason     VARCHAR(255),

    -- Optimistic locking.
                          version           BIGINT       NOT NULL DEFAULT 0,

                          created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          deleted_at        TIMESTAMPTZ,

                          CONSTRAINT pk_bookings PRIMARY KEY (id),

                          CONSTRAINT fk_bookings_room
                              FOREIGN KEY (room_id)
                                  REFERENCES rooms (id),

                          CONSTRAINT fk_bookings_organizer
                              FOREIGN KEY (organizer_id)
                                  REFERENCES users (id),

                          CONSTRAINT fk_bookings_cancelled_by
                              FOREIGN KEY (cancelled_by)
                                  REFERENCES users (id),

                          CONSTRAINT chk_bookings_status
                              CHECK (
                                  status IN (
                                             'CONFIRMED',
                                             'IN_PROGRESS',
                                             'COMPLETED',
                                             'CANCELLED',
                                             'NO_SHOW'
                                      )
                                  ),

                          CONSTRAINT chk_bookings_time
                              CHECK (start_time < end_time),

                          CONSTRAINT chk_bookings_actual
                              CHECK (
                                  actual_end_time IS NULL
                                      OR (
                                      actual_start_time IS NOT NULL
                                          AND actual_end_time >= actual_start_time
                                      )
                                  ),

    -- Booking đã hủy phải có thông tin audit.
                          CONSTRAINT chk_bookings_cancel_audit
                              CHECK (
                                  status <> 'CANCELLED'
                                      OR (
                                      cancelled_at IS NOT NULL
                                          AND cancelled_by IS NOT NULL
                                      )
                                  ),

    -- Booking đang diễn ra hoặc đã hoàn thành phải có giờ bắt đầu thực tế.
                          CONSTRAINT chk_bookings_in_progress
                              CHECK (
                                  status NOT IN ('IN_PROGRESS', 'COMPLETED')
                                      OR actual_start_time IS NOT NULL
                                  ),

    -- Booking đã hoàn thành phải có giờ kết thúc thực tế.
                          CONSTRAINT chk_bookings_completed
                              CHECK (
                                  status <> 'COMPLETED'
                                      OR actual_end_time IS NOT NULL
                                  ),

    -- Chặn trùng lịch bằng PostgreSQL.
    -- Chỉ booking chưa xóa mềm và đang chiếm phòng mới được kiểm tra.
    -- Khoảng [start, end) cho phép hai booking nối tiếp nhau.
                          CONSTRAINT excl_bookings_no_room_overlap
                              EXCLUDE USING gist (
            room_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        )
        WHERE (
            deleted_at IS NULL
            AND status IN ('CONFIRMED', 'IN_PROGRESS')
        )
);


-- ---------------------------------------------------------------------
-- 4. INDEXES
-- ---------------------------------------------------------------------

-- Xem lịch theo phòng, bao gồm lịch sử.
CREATE INDEX idx_bookings_room_start
    ON bookings (room_id, start_time, end_time);

-- Tìm phòng trống: chỉ xét booking chưa xóa mềm đang chiếm phòng.
CREATE INDEX idx_bookings_room_active_time
    ON bookings (room_id, start_time, end_time)
    WHERE deleted_at IS NULL
      AND status IN ('CONFIRMED', 'IN_PROGRESS');

-- Lịch của người tổ chức.
CREATE INDEX idx_bookings_organizer
    ON bookings (organizer_id, start_time DESC);

-- Scheduler: tìm booking đã xác nhận cần kiểm tra NO_SHOW.
CREATE INDEX idx_bookings_confirmed_start
    ON bookings (start_time)
    WHERE deleted_at IS NULL
      AND status = 'CONFIRMED';

-- Scheduler: tìm booking đang diễn ra sắp kết thúc,
-- chưa gửi thông báo nhắc trước khi hết giờ.
CREATE INDEX idx_bookings_in_progress_end
    ON bookings (end_time)
    WHERE deleted_at IS NULL
      AND status = 'IN_PROGRESS'
      AND pre_end_notified = FALSE;
