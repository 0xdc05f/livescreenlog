CREATE TABLE IF NOT EXISTS audit_log (
    id                BIGSERIAL PRIMARY KEY,
    actor_username    VARCHAR(100),
    action            VARCHAR(50) NOT NULL,
    target_session_id VARCHAR(100),
    project_key       VARCHAR(100),
    created_at        TIMESTAMPTZ DEFAULT now(),
    details           JSONB
);

COMMENT ON TABLE audit_log IS 'Basic audit log for admin actions such as viewing sessions.';

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_username);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_target ON audit_log(target_session_id);
