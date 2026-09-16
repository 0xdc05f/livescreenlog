package com.livescreenlog.app.config;

import java.util.Set;

public final class UserRoles {

    public static final Set<String> ALLOWED = Set.of("SUPER_ADMIN", "ADMIN", "VIEWER");

    private UserRoles() {}

    public static String requireAllowed(String role) {
        if (role == null || role.isBlank()) {
            return "ADMIN";
        }
        String normalized = role.trim();
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("invalid role");
        }
        return normalized;
    }
}
