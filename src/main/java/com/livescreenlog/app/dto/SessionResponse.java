package com.livescreenlog.app.dto;

import com.livescreenlog.app.domain.SessionMetadata;

import java.time.ZonedDateTime;
import java.util.Map;

public record SessionResponse(
        String sessionId,
        String projectKey,
        String projectName,
        String userId,
        String distinctId,
        String source,
        String status,
        Map<String, String> tags,
        String sdkName,
        String sdkVersion,
        String sdkIntegration,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime endAt
) {
    public static SessionResponse from(SessionMetadata metadata) {
        return from(metadata, null);
    }

    public static SessionResponse from(SessionMetadata metadata, String projectName) {
        return new SessionResponse(
                metadata.getSessionId(),
                metadata.getProjectKey(),
                projectName,
                metadata.getUserId(),
                metadata.getDistinctId(),
                metadata.getSource(),
                metadata.getStatus(),
                metadata.getTags(),
                metadata.getSdkName(),
                metadata.getSdkVersion(),
                metadata.getSdkIntegration(),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt(),
                metadata.getEndAt()
        );
    }
}
