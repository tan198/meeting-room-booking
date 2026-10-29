-- =====================================================================
-- V5: Seed permission for Role & Permission management (RBAC)
-- =====================================================================

INSERT INTO permissions (code, description) VALUES
    ('ROLE_MANAGE', 'Quản lý vai trò và gán quyền hạn (RBAC)')
ON CONFLICT (code) DO NOTHING;

-- ADMIN có toàn bộ permission mới
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

