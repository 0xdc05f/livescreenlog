CREATE INDEX IF NOT EXISTS idx_session_metadata_has_error_updated
    ON session_metadata (updated_at DESC)
    WHERE has_error = true;
