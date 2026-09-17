package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.domain.SessionMetadata;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTimeoutService {

    private final SessionMetadataRepository metadataRepository;
    private final LiveScreenLogProperties properties;
    private final ServerConfigService serverConfigService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * ACTIVE but no heartbeat for 30+ minutes → STOPPED. Runs every minute.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleActiveSessions() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(30);
        List<SessionMetadata> stale = metadataRepository.findStaleActive(cutoff);
        int expired = 0;
        for (SessionMetadata metadata : stale) {
            if (expired >= 500) {
                break;
            }
            metadata.stop();
            metadataRepository.save(metadata);
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "type", "session_stopped",
                        "sessionId", metadata.getSessionId(),
                        "projectKey", metadata.getProjectKey() != null ? metadata.getProjectKey() : "",
                        "endedAt", metadata.getEndAt() != null ? metadata.getEndAt().toString() : ""
                ));
                redisTemplate.convertAndSend("session:created", payload);
            } catch (Exception ignored) {}
            expired++;
        }
        if (expired > 0) {
            log.info("Expired {} stale ACTIVE sessions (no heartbeat for 30+ minutes)", expired);
        }
    }

    /**
     * Delete sessions older than retention.days (CASCADE removes events). Daily.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredSessions() {
        int days = serverConfigService.getEffectiveRetentionDays();
        if (days <= 0) {
            return;
        }
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(days);
        int deleted = metadataRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Retention purge removed {} sessions older than {} days", deleted, days);
        }
    }

    @Transactional
    public int manualPurge() {
        int days = serverConfigService.getEffectiveRetentionDays();
        if (days <= 0) return 0;
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(days);
        int deleted = metadataRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Manual retention purge removed {} sessions older than {} days", deleted, days);
        }
        return deleted;
    }
}
