CREATE TABLE session_metadata (
    session_id VARCHAR(255) PRIMARY KEY,
    project_key VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    distinct_id VARCHAR(255),
    source VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    end_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_session_metadata_created_at ON session_metadata(created_at);
CREATE INDEX idx_session_metadata_user_id ON session_metadata(user_id);
CREATE INDEX idx_session_metadata_source ON session_metadata(source);

CREATE TABLE session_events (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES session_metadata(session_id) ON DELETE CASCADE,
    timestamp BIGINT NOT NULL,
    event_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_events_session_timestamp ON session_events(session_id, timestamp);
