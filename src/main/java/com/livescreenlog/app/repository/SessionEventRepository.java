package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.SessionEvent;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SessionEventRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsert(List<SessionEvent> events) {
        String sql = """
                INSERT INTO session_events (session_id, timestamp, event_data)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SessionEvent event = events.get(i);
                ps.setString(1, event.sessionId());
                ps.setLong(2, event.timestamp());

                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(event.eventData());
                ps.setObject(3, jsonObject);
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
    }

    @Transactional(readOnly = true)
    public List<SessionEvent> findBySessionId(String sessionId) {
        String sql = """
                SELECT id, session_id, timestamp, event_data
                FROM session_events
                WHERE session_id = ?
                ORDER BY timestamp ASC, id ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SessionEvent(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getLong("timestamp"),
                rs.getString("event_data")
        ), sessionId);
    }

    @Transactional(readOnly = true)
    public List<SessionEvent> findBySessionIdPaged(String sessionId, Long afterId, int limit) {
        if (afterId != null) {
            String sql = """
                    SELECT id, session_id, timestamp, event_data
                    FROM session_events
                    WHERE session_id = ? AND id > ?
                    ORDER BY id ASC
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> new SessionEvent(
                    rs.getLong("id"),
                    rs.getString("session_id"),
                    rs.getLong("timestamp"),
                    rs.getString("event_data")
            ), sessionId, afterId, limit);
        }

        String sql = """
                SELECT id, session_id, timestamp, event_data
                FROM session_events
                WHERE session_id = ?
                ORDER BY id ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SessionEvent(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getLong("timestamp"),
                rs.getString("event_data")
        ), sessionId, limit);
    }
}
