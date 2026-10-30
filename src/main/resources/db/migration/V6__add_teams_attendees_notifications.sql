-- =====================================================================
-- V6: Teams/Projects, Attendees, Notifications (nhóm tính năng P1) - UUID Edition
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. TEAMS (phòng ban / dự án)
-- Đổi tên từ "groups": GROUP là từ khóa trong SQL/HQL, entity "Group" dễ gây lỗi JPQL.
-- ---------------------------------------------------------------------
CREATE TABLE teams (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_teams_code UNIQUE (code),
    CONSTRAINT chk_teams_type CHECK (type IN ('DEPARTMENT', 'PROJECT'))
);

CREATE TABLE user_teams (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    team_id     UUID        NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',

    PRIMARY KEY (user_id, team_id),
    CONSTRAINT chk_user_teams_role CHECK (member_role IN ('LEADER', 'PM', 'MEMBER'))
);

-- PK (user_id, team_id) chỉ phục vụ tra theo user. Khi "mời cả team" ta cần tra theo team_id.
CREATE INDEX idx_user_teams_team ON user_teams (team_id);

-- ---------------------------------------------------------------------
-- 2. BOOKING_ATTENDEES
-- Khi mời theo team: mở rộng thành từng cá nhân và LƯU SNAPSHOT tại thời điểm đặt,
-- nên thành viên vào team sau đó không tự động xuất hiện trong cuộc họp cũ.
-- Organizer cũng là 1 dòng (role = ORGANIZER) để "lịch của tôi" chỉ cần 1 truy vấn.
-- ---------------------------------------------------------------------
CREATE TABLE booking_attendees (
    booking_id      UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id),
    attendee_role   VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    rsvp_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decline_reason  VARCHAR(255),
    responded_at    TIMESTAMPTZ,

    -- PK ghép: vừa khử trùng lặp người tham gia, vừa bỏ được surrogate id + 1 index thừa.
    PRIMARY KEY (booking_id, user_id),
    CONSTRAINT chk_attendee_role CHECK (attendee_role IN ('ORGANIZER', 'REQUIRED', 'OPTIONAL')),
    CONSTRAINT chk_attendee_rsvp CHECK (rsvp_status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

-- "Lịch của tôi" + kiểm tra trùng lịch cá nhân: tra theo user_id.
-- FK không tự có index trong PostgreSQL!
CREATE INDEX idx_attendees_user ON booking_attendees (user_id, booking_id);

-- ---------------------------------------------------------------------
-- 3. NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    booking_id  UUID         REFERENCES bookings (id) ON DELETE SET NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     TEXT         NOT NULL,
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    read_at     TIMESTAMPTZ,
    dedupe_key  VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_notifications_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread       ON notifications (user_id) WHERE read_at IS NULL;

-- Seed thêm quyền quản lý Team cho RBAC
INSERT INTO permissions (code, description) VALUES
    ('TEAM_MANAGE', 'Quản lý phòng ban / dự án và thành viên')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

