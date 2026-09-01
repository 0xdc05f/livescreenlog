package com.livescreenlog.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final List<String> WEAK_HMAC_MARKERS = List.of(
            "default-hmac-secret",
            "change-me",
            "changeme",
            "need-to-change",
            "admin-password"
    );

    private static final List<String> WEAK_PASSWORD_MARKERS = List.of(
            "admin", "password", "123456", "admin123", "change-me", "changeme", "default", "secret"
    );

    private final LiveScreenLogProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String hmac = properties.getHmacSecret();
        if (hmac == null || hmac.length() < 32 || isWeak(hmac)) {
            throw new IllegalStateException(
                    "LIVESCREENLOG_HMAC_SECRET must be set to a strong secret (>=32 chars) in production");
        }

        List<String> origins = properties.getAllowedCaptureOrigins();
        if (origins == null || origins.isEmpty() || origins.stream().anyMatch(o -> "*".equals(o.trim()))) {
            throw new IllegalStateException(
                    "LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS must be an explicit allow-list in production (no *)");
        }

        // Validate initial admin password for bootstrap in prod (if provided)
        String dashPass = properties.getDashboardPassword();
        if (dashPass != null && !dashPass.isBlank() && isWeakPassword(dashPass)) {
            throw new IllegalStateException(
                    "Initial admin password (livescreenlog.security.dashboard-password) is too weak for production. Use a strong password (>=12 chars, not common words).");
        }

        log.info("Production security checks passed");
    }

    private boolean isWeak(String value) {
        String lower = value.toLowerCase();
        return WEAK_HMAC_MARKERS.stream().anyMatch(lower::contains);
    }

    private boolean isWeakPassword(String pwd) {
        if (pwd == null || pwd.length() < 12) return true;
        String lower = pwd.toLowerCase();
        return WEAK_PASSWORD_MARKERS.stream().anyMatch(lower::contains);
    }
}
