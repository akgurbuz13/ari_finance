-- V016: Add role column to users for RBAC support
-- Default 'USER' auto-backfills all existing rows (catalog-only, fast on any table size)

ALTER TABLE identity.users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';

ALTER TABLE identity.users
    ADD CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));

CREATE INDEX idx_users_role ON identity.users(role);

-- To seed an initial admin after deployment:
-- UPDATE identity.users SET role = 'ADMIN' WHERE email = 'admin@ova.app';
