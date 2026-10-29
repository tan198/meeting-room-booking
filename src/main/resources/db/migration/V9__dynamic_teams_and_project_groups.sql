-- =====================================================================
-- V9: Loại bỏ ràng buộc Enum cứng trên Teams và Work_Groups.
--   - Admin tự tạo Team (VD: Team Dev, Team BA, Team PM...) và tự định nghĩa
--     vị trí/vai trò dạng chuỗi động (String), gán trực tiếp khi tạo User.
--   - Admin tự tạo Group / Dự án (VD: Dự án A, Dự án B, Phòng Kỹ thuật...)
--     và chỉ định một số thành viên từ các Team tham gia vào Dự án đó.
--   - Khi đặt phòng họp (Booking): Người tạo chọn phạm vi cuộc họp theo Team
--     (team_id) HOẶC theo Dự án/Group (group_id) và chọn những người liên quan
--     để mời họp.
-- =====================================================================

-- 1. Bỏ các CHECK constraint bó cứng giá trị kiểu Enum
ALTER TABLE teams
    DROP CONSTRAINT IF EXISTS chk_teams_type;

ALTER TABLE user_teams
    DROP CONSTRAINT IF EXISTS chk_user_teams_role;

ALTER TABLE work_groups
    DROP CONSTRAINT IF EXISTS chk_work_groups_type;

-- 2. Cho phép type của teams và work_groups là chuỗi tự do do Admin đặt (hoặc mặc định)
ALTER TABLE teams
    ALTER COLUMN type TYPE VARCHAR(50),
    ALTER COLUMN type SET DEFAULT 'TEAM';

ALTER TABLE user_teams
    ALTER COLUMN member_role TYPE VARCHAR(50),
    ALTER COLUMN member_role SET DEFAULT 'MEMBER';

ALTER TABLE work_groups
    ALTER COLUMN type TYPE VARCHAR(50),
    ALTER COLUMN type SET DEFAULT 'PROJECT';

-- 3. Bổ sung cột team_id trên bảng bookings để cuộc họp có thể gắn chỉ mục
--    theo Team (team_id) HOẶC theo Dự án / Group (group_id)
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES teams (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_team ON bookings (team_id) WHERE team_id IS NOT NULL;

