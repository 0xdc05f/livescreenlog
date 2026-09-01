package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import com.livescreenlog.app.service.ServerConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTimeoutService {

    private final SessionMetadataRepository metadataRepository;
    private final LiveScreenLogProperties properties;
    private final ServerConfigService serverConfigService;

    /**
     * ACTIVE but no heartbeat for 30+ minutes → STOPPED. Runs every minute.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleActiveSessions() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(30);
        int expired = metadataRepository.markStaleSessionsStopped(cutoff);
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
