-- V6: Simple key-value store for runtime-overridable server settings (safe ones only)
CREATE TABLE IF NOT EXISTS server_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE server_config IS 'Global server configuration overrides (e.g. retention, fallback project key). Sensitive values like HMAC_SECRET must stay in environment variables.';
