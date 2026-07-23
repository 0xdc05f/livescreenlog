package com.livescreenlog.app.dto;

public record SessionCreateResponse(
        String sessionId,
        String token,
        boolean enabled,
        String recordingMode
) {}
