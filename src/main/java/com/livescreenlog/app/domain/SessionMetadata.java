package com.livescreenlog.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(name = "session_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionMetadata {

    @Id
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "distinct_id")
    private String distinctId;

    @Column(name = "source")
    private String source;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, String> tags;

    @Column(name = "sdk_name", length = 64)
    private String sdkName;

    @Column(name = "sdk_version", length = 32)
    private String sdkVersion;

    @Column(name = "sdk_integration", length = 32)
    private String sdkIntegration;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "end_at")
    private ZonedDateTime endAt;

    @Builder
    public SessionMetadata(
            String sessionId,
            String projectKey,
            String userId,
            String distinctId,
            String source,
            String status,
            Map<String, String> tags,
            String sdkName,
            String sdkVersion,
            String sdkIntegration,
            ZonedDateTime endAt
    ) {
        this.sessionId = sessionId;
        this.projectKey = projectKey;
        this.userId = userId;
        this.distinctId = distinctId;
        this.source = source;
        this.status = status;
        this.tags = tags;
        this.sdkName = sdkName;
        this.sdkVersion = sdkVersion;
        this.sdkIntegration = sdkIntegration;
        this.endAt = endAt;
    }

    public void stop() {
        this.status = "STOPPED";
        this.endAt = ZonedDateTime.now();
    }

    public void heartbeat() {
        this.updatedAt = ZonedDateTime.now();
    }
}
