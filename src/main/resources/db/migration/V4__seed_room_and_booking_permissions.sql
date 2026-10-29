-- =====================================================================
-- V4: Seed permissions for Room & Booking modules
-- =====================================================================

INSERT INTO permissions (code, description) VALUES
    ('ROOM_CREATE',    'Tạo phòng họp mới'),
    ('ROOM_UPDATE',    'Cập nhật thông tin và trạng thái phòng họp'),
    ('ROOM_DELETE',    'Xóa mềm phòng họp'),
    ('BOOKING_MANAGE', 'Quản lý toàn bộ lịch đặt phòng của hệ thống')
ON CONFLICT (code) DO NOTHING;

-- ADMIN có toàn bộ permission mới
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
