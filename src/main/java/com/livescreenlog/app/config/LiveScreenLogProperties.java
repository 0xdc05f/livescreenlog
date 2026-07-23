package com.livescreenlog.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "livescreenlog")
@Getter
@Setter
public class LiveScreenLogProperties {

    private final Security security = new Security();
    private final Retention retention = new Retention();
    private final RateLimit rateLimit = new RateLimit();
    private final Limits limits = new Limits();

    @Getter
    @Setter
    public static class Security {
        private String hmacSecret;
        private String projectKey;
        private List<String> allowedCaptureOrigins;
        private boolean dashboardEnabled = false;
        private String dashboardUsername = "admin";
        private String dashboardPassword;
    }

    @Getter
    @Setter
    public static class Retention {
        /** Days to keep sessions; 0 disables retention cleanup. */
        private int days = 30;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int sessionCreatePerMinute = 60;
        private int eventAppendPerMinute = 600;
    }

    @Getter
    @Setter
    public static class Limits {
        /** Max events accepted in a single POST /api/events body. */
        private int maxEventBatchSize = 500;
        /** Max raw JSON body size for POST /api/events (bytes). */
        private int maxEventPayloadBytes = 1_048_576;
        /** Default page size for GET .../events?paged=true. */
        private int defaultEventPageSize = 2000;
        /** Hard cap for event page size. */
        private int maxEventPageSize = 5000;
        /** Hard cap when full dump (paged=false) is requested. */
        private int maxEventFullDumpSize = 10_000;
        /** Max page size for session search. */
        private int maxSearchPageSize = 100;
        /** Skip metadata.heartbeat() if updated within this many seconds. */
        private int heartbeatThrottleSeconds = 30;
        /** Max concurrent live SSE subscribers per session. */
        private int maxLiveSsePerSession = 20;
        /** Max concurrent push SSE connections per userId. */
        private int maxPushSsePerUser = 5;
        /** Max concurrent push SSE connections process-wide. */
        private int maxPushSseTotal = 5_000;
    }

    // Convenience accessors used across the codebase
    public String getHmacSecret() {
        return security.getHmacSecret();
    }

    public String getProjectKey() {
        return security.getProjectKey();
    }

    public List<String> getAllowedCaptureOrigins() {
        return security.getAllowedCaptureOrigins();
    }

    public boolean isDashboardEnabled() {
        return security.isDashboardEnabled();
    }

    public String getDashboardUsername() {
        return security.getDashboardUsername();
    }

    public String getDashboardPassword() {
        return security.getDashboardPassword();
    }

    public int getRetentionDays() {
        return retention.getDays();
    }

    public int getSessionCreatePerMinute() {
        return rateLimit.getSessionCreatePerMinute();
    }

    public int getEventAppendPerMinute() {
        return rateLimit.getEventAppendPerMinute();
    }

    public int getMaxEventBatchSize() {
        return limits.getMaxEventBatchSize();
    }

    public int getMaxEventPayloadBytes() {
        return limits.getMaxEventPayloadBytes();
    }

    public int getDefaultEventPageSize() {
        return limits.getDefaultEventPageSize();
    }

    public int getMaxEventPageSize() {
        return limits.getMaxEventPageSize();
    }

    public int getMaxEventFullDumpSize() {
        return limits.getMaxEventFullDumpSize();
    }

    public int getMaxSearchPageSize() {
        return limits.getMaxSearchPageSize();
    }

    public int getHeartbeatThrottleSeconds() {
        return limits.getHeartbeatThrottleSeconds();
    }

    public int getMaxLiveSsePerSession() {
        return limits.getMaxLiveSsePerSession();
    }

    public int getMaxPushSsePerUser() {
        return limits.getMaxPushSsePerUser();
    }

    public int getMaxPushSseTotal() {
        return limits.getMaxPushSseTotal();
    }
}
