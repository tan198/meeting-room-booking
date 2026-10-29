-- =====================================================================
-- V3: refresh tokens + seed base roles / permissions
-- =====================================================================

-- Refresh token chỉ lưu bản băm SHA-256 (hex), không lưu token gốc.
CREATE TABLE refresh_tokens (
                                id         UUID        NOT NULL DEFAULT gen_random_uuid(),
                                user_id    UUID        NOT NULL,
                                token_hash VARCHAR(64) NOT NULL,
                                expires_at TIMESTAMPTZ NOT NULL,
                                revoked_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------
-- Seed roles
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Quản trị hệ thống'),
    ('USER', 'Người dùng thông thường')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------
-- Seed permissions
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('USER_READ',   'Xem danh sách và chi tiết người dùng'),
    ('USER_CREATE', 'Tạo người dùng'),
    ('USER_UPDATE', 'Cập nhật người dùng'),
    ('USER_DELETE', 'Xóa người dùng')
ON CONFLICT (code) DO NOTHING;

-- ADMIN có toàn bộ permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
