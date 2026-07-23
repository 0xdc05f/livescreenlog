package com.livescreenlog.app.dto;

public record ProjectSettingsRequest(
        String recordingMode,
        String targetUsers
) {}
