-- Common filter + sort combos
CREATE INDEX IF NOT EXISTS idx_session_metadata_status_created_at
    ON session_metadata (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_metadata_project_created_at
    ON session_metadata (project_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_metadata_source_created_at
    ON session_metadata (source, created_at DESC);

-- Free-text query uses lower(col) LIKE '%x%' in SessionReadService
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_session_metadata_user_id_lower_trgm
    ON session_metadata USING gin (lower(user_id) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_session_metadata_source_lower_trgm
    ON session_metadata USING gin (lower(source) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_session_metadata_distinct_id_lower_trgm
    ON session_metadata USING gin (lower(distinct_id) gin_trgm_ops);
