package com.livescreenlog.app.dto;

import java.time.OffsetDateTime;

public record ProjectDto(
        Long id,
        String name,
        String description,
        String apiKey,
        String recordingMode,
        String targetUsers,
        OffsetDateTime createdAt
) {}
