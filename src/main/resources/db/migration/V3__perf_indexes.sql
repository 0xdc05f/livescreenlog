-- Cursor pagination on session_events uses ORDER BY id
CREATE INDEX IF NOT EXISTS idx_session_events_session_id_id
    ON session_events (session_id, id);

-- Heartbeat / stale-session scans filter ACTIVE + updated_at
CREATE INDEX IF NOT EXISTS idx_session_metadata_status_updated_at
    ON session_metadata (status, updated_at);
