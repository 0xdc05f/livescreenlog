package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.SessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface SessionMetadataRepository extends JpaRepository<SessionMetadata, String>, JpaSpecificationExecutor<SessionMetadata> {

    /**
     * ACTIVE 상태이며 updatedAt이 cutoff 이전인 세션을 STOPPED 로 일괄 변경.
     * @return 변경된 건수
     */
    @Modifying
    @Query("""
        UPDATE SessionMetadata s
        SET s.status = 'STOPPED', s.endAt = CURRENT_TIMESTAMP
        WHERE s.status = 'ACTIVE' AND s.updatedAt < :cutoff
    """)
    int markStaleSessionsStopped(@Param("cutoff") ZonedDateTime cutoff);

    @Modifying
    @Query("DELETE FROM SessionMetadata s WHERE s.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") ZonedDateTime cutoff);

    @Modifying
    @Query("UPDATE SessionMetadata s SET s.updatedAt = :now WHERE s.sessionId = :id")
    int touchUpdatedAt(@Param("id") String id, @Param("now") ZonedDateTime now);

    @Query(value = """
        SELECT COUNT(DISTINCT user_id) FROM session_metadata
        WHERE status = 'ACTIVE' AND updated_at >= :cutoff
          AND user_id IS NOT NULL AND user_id <> ''
          AND (CAST(:projectKey AS text) IS NULL OR project_key = :projectKey)
          AND (:unrestricted = true OR project_key IN (:allowed))
        """, nativeQuery = true)
    long countLiveUsers(@Param("cutoff") ZonedDateTime cutoff,
                        @Param("projectKey") String projectKey,
                        @Param("unrestricted") boolean unrestricted,
                        @Param("allowed") List<String> allowed);

    @Query(value = """
        SELECT COUNT(*) FROM session_metadata
        WHERE status = 'ACTIVE' AND updated_at >= :cutoff
          AND (CAST(:projectKey AS text) IS NULL OR project_key = :projectKey)
          AND (:unrestricted = true OR project_key IN (:allowed))
        """, nativeQuery = true)
    long countLiveSessions(@Param("cutoff") ZonedDateTime cutoff,
                           @Param("projectKey") String projectKey,
                           @Param("unrestricted") boolean unrestricted,
                           @Param("allowed") List<String> allowed);

    @Query(value = """
        SELECT to_char(date_trunc('hour', created_at), 'YYYY-MM-DD"T"HH24:00') AS b,
               COUNT(*) AS sessions,
               COUNT(DISTINCT user_id) AS users
        FROM session_metadata
        WHERE created_at >= :from AND created_at < :to
          AND (CAST(:projectKey AS text) IS NULL OR project_key = :projectKey)
          AND (:unrestricted = true OR project_key IN (:allowed))
        GROUP BY 1 ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> usageByHour(@Param("from") ZonedDateTime from,
                               @Param("to") ZonedDateTime to,
                               @Param("projectKey") String projectKey,
                               @Param("unrestricted") boolean unrestricted,
                               @Param("allowed") List<String> allowed);

    @Query(value = """
        SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS b,
               COUNT(*) AS sessions,
               COUNT(DISTINCT user_id) AS users
        FROM session_metadata
        WHERE created_at >= :from AND created_at < :to
          AND (CAST(:projectKey AS text) IS NULL OR project_key = :projectKey)
          AND (:unrestricted = true OR project_key IN (:allowed))
        GROUP BY 1 ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> usageByDay(@Param("from") ZonedDateTime from,
                              @Param("to") ZonedDateTime to,
                              @Param("projectKey") String projectKey,
                              @Param("unrestricted") boolean unrestricted,
                              @Param("allowed") List<String> allowed);

    @Query(value = """
        SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS b,
               COUNT(*) AS sessions,
               COUNT(DISTINCT user_id) AS users
        FROM session_metadata
        WHERE created_at >= :from AND created_at < :to
          AND (CAST(:projectKey AS text) IS NULL OR project_key = :projectKey)
          AND (:unrestricted = true OR project_key IN (:allowed))
        GROUP BY 1 ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> usageByMonth(@Param("from") ZonedDateTime from,
                                @Param("to") ZonedDateTime to,
                                @Param("projectKey") String projectKey,
                                @Param("unrestricted") boolean unrestricted,
                                @Param("allowed") List<String> allowed);
}

