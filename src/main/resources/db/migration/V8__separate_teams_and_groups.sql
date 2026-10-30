-- =====================================================================
-- V8: Tách biệt rõ 2 thực thể:
--   1. TEAMS & USER_TEAMS: Nhóm chuyên môn (Backend Team, QA Team, Leadership...)
--      trong đó mỗi thành viên mang vị trí chuyên môn (DEV, PM, BA, QA, LEADER...).
--      Hỗ trợ gán ngay khi tạo/cập nhật User và mời họp theo cả Team hoặc lọc theo vị trí.
--   2. WORK_GROUPS & USER_GROUPS: Đơn vị quản lý theo Phòng ban (DEPARTMENT)
--      hoặc Dự án (PROJECT) do Admin tạo để gom thành viên và gắn chỉ mục (group_id)
--      quản lý các cuộc họp rõ ràng.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Chuẩn hóa lại bảng TEAMS & USER_TEAMS cho Nhóm chuyên môn
-- ---------------------------------------------------------------------
ALTER TABLE teams
    DROP CONSTRAINT IF EXISTS chk_teams_type;

ALTER TABLE teams
    ALTER COLUMN type SET DEFAULT 'FUNCTIONAL';

ALTER TABLE user_teams
    DROP CONSTRAINT IF EXISTS chk_user_teams_role;

ALTER TABLE user_teams
    ALTER COLUMN member_role SET DEFAULT 'DEV',
    ADD CONSTRAINT chk_user_teams_role
        CHECK (member_role IN ('DEV', 'PM', 'BA', 'QA', 'DESIGNER', 'DEVOPS', 'LEADER', 'MEMBER'));

-- ---------------------------------------------------------------------
-- 2. Tạo bảng WORK_GROUPS (Phòng ban / Dự án) & USER_GROUPS
-- (Đặt tên bảng vật lý là work_groups vì GROUP là từ khóa SQL)
-- ---------------------------------------------------------------------
CREATE TABLE work_groups (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_work_groups_code UNIQUE (code),
    CONSTRAINT chk_work_groups_type CHECK (type IN ('DEPARTMENT', 'PROJECT'))
);

CREATE TABLE user_groups (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID        NOT NULL REFERENCES work_groups (id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, group_id)
);

CREATE INDEX idx_user_groups_group ON user_groups (group_id);

-- ---------------------------------------------------------------------
-- 3. Gắn chỉ mục Group (Phòng ban / Dự án) vào bảng BOOKINGS
-- để quản lý và thống kê các cuộc họp theo từng Phòng ban / Dự án rõ ràng
-- ---------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN group_id UUID REFERENCES work_groups (id) ON DELETE SET NULL;

CREATE INDEX idx_bookings_group ON bookings (group_id) WHERE group_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 4. Seed quyền GROUP_MANAGE
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('GROUP_MANAGE', 'Quản lý Group (Phòng ban / Dự án) và phân bổ thành viên')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

