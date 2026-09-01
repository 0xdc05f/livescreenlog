-- V7: Users / Accounts for dashboard access + foundation for RBAC
-- Super admin can be bootstrapped on first start via properties.

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    role          VARCHAR(50) NOT NULL DEFAULT 'SUPER_ADMIN',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE users IS 'Application users (dashboard / admin accounts). Role is coarse for now (SUPER_ADMIN, ADMIN, VIEWER).';

CREATE TABLE IF NOT EXISTS user_projects (
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id       BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role_in_project  VARCHAR(50) NOT NULL DEFAULT 'VIEWER',   -- future: OWNER, ADMIN, VIEWER, etc.
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);

COMMENT ON TABLE user_projects IS 'Associates users with projects. Used for future per-account project scoping and permissions.';

CREATE INDEX IF NOT EXISTS idx_user_projects_project ON user_projects(project_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);  -- for filtering active admins
CREATE INDEX IF NOT EXISTS idx_users_role_enabled ON users(role, enabled);  -- common query pattern for auth
