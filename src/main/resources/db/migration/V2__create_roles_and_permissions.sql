-- =====================================================================
-- V2: Schema RBAC (P1) - roles, permissions, role_permissions,
--     replace column users.role with users.role_id
-- =====================================================================

CREATE TABLE roles (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name VARCHAR(50) NOT NULL UNIQUE,
                       description VARCHAR(255),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             code VARCHAR(100) NOT NULL UNIQUE,
                             description VARCHAR(255),
                             created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
                                  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                  permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_perm_id ON role_permissions(permission_id);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_role,
    DROP COLUMN role,
    ADD COLUMN role_id UUID NOT NULL REFERENCES roles(id);

CREATE INDEX idx_users_role_id ON users(role_id);