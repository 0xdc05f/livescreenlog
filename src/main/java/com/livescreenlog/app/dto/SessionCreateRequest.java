package com.livescreenlog.app.dto;

import java.util.Map;

public record SessionCreateRequest(
        String projectKey,
        String apiKey,
        String userId,
        String distinctId,
        String source,
        String trigger,
        Map<String, String> tags,
        String sdkName,
        String sdkVersion,
        String sdkIntegration
) {
    /** Prefer projectKey; fall back to apiKey alias. */
    public String effectiveProjectKey() {
        if (projectKey != null && !projectKey.isBlank()) {
            return projectKey;
        }
        return apiKey;
    }
}
