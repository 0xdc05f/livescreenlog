CREATE INDEX IF NOT EXISTS idx_session_metadata_project_status_created
  ON session_metadata (project_key, status, created_at DESC);
