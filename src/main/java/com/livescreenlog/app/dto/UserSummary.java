package com.livescreenlog.app.dto;

import java.time.OffsetDateTime;

public record UserSummary(Long id, String username, String email, String role, boolean enabled, OffsetDateTime createdAt) {}
