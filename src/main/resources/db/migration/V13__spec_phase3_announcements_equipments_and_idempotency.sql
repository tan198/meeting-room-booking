-- =====================================================================
-- V13: Nâng cấp theo chuẩn nghiệm thu meeting-room-spec.md (Phase 3):
--   1. Danh mục Thiết bị phòng họp (equipments) có mã code chuẩn hóa và trạng thái ACTIVE / INACTIVE.
--   2. Thông báo phát hành chung (announcements) lưu vết người phát hành, phạm vi audience và danh sách người nhận đã chốt.
--   3. Bảng lưu khóa chống gửi trùng (idempotency_keys) cho các thao tác POST quan trọng trong 24 giờ.
-- =====================================================================

-- 1. Bảng danh mục thiết bị (equipments - Spec mục 6.2 & 21.4)
CREATE TABLE IF NOT EXISTS equipments (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_equipments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Seed danh mục thiết bị tiêu chuẩn (nếu chưa có)
INSERT INTO equipments (code, name, description, status)
VALUES
    ('PROJECTOR', 'Máy chiếu độ phân giải cao', 'Máy chiếu tiêu chuẩn hỗ trợ cổng HDMI/Type-C', 'ACTIVE'),
    ('TV', 'Màn hình TV thông minh', 'Màn hình LED/Smart TV trình chiếu không dây', 'ACTIVE'),
    ('WHITEBOARD', 'Bảng trắng viết bút lông', 'Bảng từ trắng kèm bút và khăn lau', 'ACTIVE'),
    ('VIDEO_CONFERENCE', 'Hệ thống họp trực tuyến (Camera + Mic đa hướng)', 'Thiết bị hội nghị truyền hình trực tuyến', 'ACTIVE'),
    ('SOUND_SYSTEM', 'Hệ thống âm thanh & Micro không dây', 'Loa hội trường và micro không dây', 'ACTIVE'),
    ('AIR_CONDITIONER', 'Điều hòa nhiệt độ', 'Hệ thống điều hòa làm mát phòng họp', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- 2. Bảng thông báo chung do Quản trị viên phát hành (announcements - Spec mục 12.3 & 22 UC-04)
CREATE TABLE IF NOT EXISTS announcements (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,
    audience_type    VARCHAR(30)  NOT NULL,
    audience_ids     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    recipient_ids    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    recipient_count  INT          NOT NULL DEFAULT 0,
    related_type     VARCHAR(40),
    related_id       UUID,
    correction_of_id UUID         REFERENCES announcements (id) ON DELETE SET NULL,
    published_by     UUID         NOT NULL REFERENCES users (id),
    published_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_announcements_audience_type CHECK (
        audience_type IN ('ALL', 'DEPARTMENTS', 'TEAMS', 'PROJECTS', 'BOOKINGS')
    )
);

CREATE INDEX IF NOT EXISTS idx_announcements_published_at
    ON announcements (published_at DESC);

-- 3. Bảng khóa chống gửi lặp Idempotency-Key (Spec mục 15, 23.4 & AC-30)
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operation_key   VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INT          NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_user_op_key UNIQUE (user_id, operation_key, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
    ON idempotency_keys (expires_at);

