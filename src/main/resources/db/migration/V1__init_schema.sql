-- =====================================================================
-- V1: Schema lõi (P0) - users, rooms, bookings (UUID v4 Edition)
-- Nguyên tắc: mọi bất biến (invariant) quan trọng phải được DB bảo vệ,
-- app chỉ là lớp kiểm tra "thân thiện" phía trước.
-- =====================================================================

-- btree_gist cho phép trộn toán tử "=" (room_id kiểu UUID) với "&&" (range) trong 1 GiST index.
-- Là extension "trusted" từ PG13 nên DB owner tạo được, không cần superuser.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Lưu ý: Từ PostgreSQL 13+, hàm gen_random_uuid() (chuẩn UUID v4) đã có sẵn trong core,
-- không cần cài thêm extension "uuid-ossp" hay "pgcrypto".

-- ---------------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------------
CREATE TABLE users (
                       id            UUID          DEFAULT gen_random_uuid() PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(100) NOT NULL,
                       role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
                       is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                       CONSTRAINT uq_users_email      UNIQUE (email),
    -- Ép email luôn lưu dạng chữ thường: tránh "Admin@x.com" và "admin@x.com" thành 2 tài khoản.
    -- App phải lowercase email trước khi lưu / tra cứu.
                       CONSTRAINT chk_users_email_lc  CHECK (email = lower(email)),
                       CONSTRAINT chk_users_role      CHECK (role IN ('USER', 'ADMIN'))
);

-- ---------------------------------------------------------------------
-- 2. ROOMS
-- ---------------------------------------------------------------------
CREATE TABLE rooms (
                       id         UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
                       name       VARCHAR(100) NOT NULL,
                       floor      INT          NOT NULL,  -- INT thay vì SMALLINT: khớp kiểu int của Java, tránh lỗi Hibernate ddl-auto=validate
                       capacity   INT          NOT NULL,
    -- Với 10 phòng, JSONB array đủ dùng. Nếu cần lọc/thống kê tiện ích phức tạp
    -- thì tách bảng facilities + room_facilities (xem ghi chú trade-off trong README).
                       facilities JSONB        NOT NULL DEFAULT '[]'::jsonb,
                       status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                       CONSTRAINT uq_rooms_name        UNIQUE (name),
                       CONSTRAINT chk_rooms_capacity   CHECK (capacity > 0),
                       CONSTRAINT chk_rooms_status     CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE')),
                       CONSTRAINT chk_rooms_facilities CHECK (jsonb_typeof(facilities) = 'array')
);

-- ---------------------------------------------------------------------
-- 3. BOOKINGS
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
                          id                UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
                          room_id           UUID         NOT NULL REFERENCES rooms (id),
                          organizer_id      UUID         NOT NULL REFERENCES users (id),
                          title             VARCHAR(200) NOT NULL,
                          description       TEXT,

    -- Khung giờ ĐÃ ĐẶT (theo kế hoạch)
                          start_time        TIMESTAMPTZ  NOT NULL,
                          end_time          TIMESTAMPTZ  NOT NULL,

    -- Thời gian THỰC TẾ (điền khi check-in / check-out)
                          actual_start_time TIMESTAMPTZ,
                          actual_end_time   TIMESTAMPTZ,

                          status            VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',

    -- Cờ đánh dấu đã gửi thông báo nhắc trước khi hết giờ (tránh Scheduler bắn lặp lại mỗi phút)
                          pre_end_notified  BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Thông tin hủy (audit: ai hủy, khi nào, vì sao - quan trọng cho Admin Override)
                          cancelled_by      UUID         REFERENCES users (id),
                          cancelled_at      TIMESTAMPTZ,
                          cancel_reason     VARCHAR(255),

    -- Optimistic locking (@Version): chống 2 thao tác đổi trạng thái đồng thời
    -- (vd: scheduler đánh NO_SHOW cùng lúc user bấm check-in).
                          version           BIGINT       NOT NULL DEFAULT 0,
                          created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                          CONSTRAINT chk_bookings_status
                              CHECK (status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
                          CONSTRAINT chk_bookings_time
                              CHECK (start_time < end_time),
                          CONSTRAINT chk_bookings_actual
                              CHECK (actual_end_time IS NULL
                                  OR (actual_start_time IS NOT NULL AND actual_end_time >= actual_start_time)),
    -- Trạng thái và dữ liệu đi kèm phải nhất quán
                          CONSTRAINT chk_bookings_cancel_audit
                              CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)),
                          CONSTRAINT chk_bookings_in_progress
                              CHECK (status NOT IN ('IN_PROGRESS', 'COMPLETED') OR actual_start_time IS NOT NULL),
                          CONSTRAINT chk_bookings_completed
                              CHECK (status <> 'COMPLETED' OR actual_end_time IS NOT NULL),

    -- *** CHỐT CHẶN TRÙNG LỊCH ***
    -- Cùng phòng + khoảng thời gian [start, end) giao nhau + booking còn "chiếm phòng" => bị chặn.
    -- Khoảng nửa mở [) cho phép 09:00-10:00 nối tiếp 10:00-11:00.
    -- CANCELLED / NO_SHOW / COMPLETED không nằm trong WHERE => tự động giải phóng phòng.
                          CONSTRAINT excl_bookings_no_room_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('CONFIRMED', 'IN_PROGRESS'))
);

-- Xem lịch theo phòng + khoảng thời gian bằng điều kiện thường (phục vụ cả hiển thị lịch sử)
CREATE INDEX idx_bookings_room_start ON bookings (room_id, start_time, end_time);

-- Tối ưu riêng cho truy vấn Tìm Phòng Trống (NOT EXISTS) chỉ quét các lịch đang chiếm phòng
CREATE INDEX idx_bookings_room_active_time ON bookings (room_id, start_time, end_time)
    WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

-- "Lịch của tôi" (phần tôi là người tổ chức)
CREATE INDEX idx_bookings_organizer ON bookings (organizer_id, start_time DESC);

-- Scheduler: quét booking quá 15 phút chưa check-in => NO_SHOW
CREATE INDEX idx_bookings_confirmed_start ON bookings (start_time)
    WHERE status = 'CONFIRMED';

-- Scheduler: quét cuộc họp sắp kết thúc (còn 20 phút) chưa được gửi thông báo
CREATE INDEX idx_bookings_in_progress_end ON bookings (end_time)
    WHERE status = 'IN_PROGRESS' AND pre_end_notified = FALSE;