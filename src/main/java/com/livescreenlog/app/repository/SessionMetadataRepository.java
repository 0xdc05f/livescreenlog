package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.SessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

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
}

