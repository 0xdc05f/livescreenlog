ALTER TABLE session_metadata ADD COLUMN IF NOT EXISTS tags JSONB;
ALTER TABLE session_metadata ADD COLUMN IF NOT EXISTS sdk_name VARCHAR(64);
ALTER TABLE session_metadata ADD COLUMN IF NOT EXISTS sdk_version VARCHAR(32);
ALTER TABLE session_metadata ADD COLUMN IF NOT EXISTS sdk_integration VARCHAR(32);
