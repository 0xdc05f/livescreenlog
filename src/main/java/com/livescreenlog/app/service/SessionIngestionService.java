package com.livescreenlog.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.domain.SessionEvent;
import com.livescreenlog.app.domain.SessionMetadata;
import com.livescreenlog.app.dto.SessionCreateRequest;
import com.livescreenlog.app.dto.SessionCreateResponse;
import com.livescreenlog.app.repository.ProjectRepository;
import com.livescreenlog.app.repository.SessionEventRepository;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import com.livescreenlog.app.web.ApiExceptionHandler.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionIngestionService {

    private final SessionMetadataRepository metadataRepository;
    private final SessionEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final LiveScreenLogProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    @Transactional
    public SessionCreateResponse createSession(SessionCreateRequest request, String clientKey) {
        if (!rateLimitService.allowSessionCreate(clientKey != null ? clientKey : "unknown")) {
            throw new RateLimitExceededException("Session create rate limit exceeded");
        }

        String effectiveKey = request.effectiveProjectKey();
        if (effectiveKey == null || effectiveKey.isBlank()) {
            throw new IllegalArgumentException("projectKey or apiKey is required");
        }

        com.livescreenlog.app.domain.Project project = projectRepository.findByApiKey(effectiveKey).orElse(null);
        boolean isGlobalKey = properties.getProjectKey() != null
                && properties.getProjectKey().equals(effectiveKey);
        if (project == null && !isGlobalKey) {
            throw new IllegalArgumentException("Invalid project key");
        }

        String mode = (project != null) ? project.getRecordingMode() : "ALL";
        String targetUsers = (project != null) ? project.getTargetUsers() : null;

        boolean enabled = false;

        if ("ERROR".equals(request.trigger()) || "FORCE".equals(request.trigger())) {
            enabled = true;
        } else if ("ALL".equals(mode)) {
            enabled = true;
        } else if ("NONE".equals(mode)) {
            enabled = false;
        } else if ("A".equals(mode)) {
            if (targetUsers != null && request.userId() != null) {
                String userId = request.userId().trim();
                for (String tUser : targetUsers.split(",")) {
                    if (tUser.trim().equals(userId)) {
                        enabled = true;
                        break;
                    }
                }
            }
        } else if ("B".equals(mode) || "C".equals(mode)) {
            enabled = false;
        }

        if (!enabled) {
            return new SessionCreateResponse(null, null, false, mode);
        }

        String sessionId = UUID.randomUUID().toString();
        SessionMetadata metadata = SessionMetadata.builder()
                .sessionId(sessionId)
                .projectKey(effectiveKey)
                .userId(request.userId())
                .distinctId(request.distinctId())
                .source(request.source())
                .status("ACTIVE")
                .tags(request.tags())
                .sdkName(request.sdkName())
                .sdkVersion(request.sdkVersion())
                .sdkIntegration(request.sdkIntegration())
                .build();

        metadataRepository.save(metadata);

        long expirationMillis = System.currentTimeMillis() + (24 * 60 * 60 * 1000L);
        String token = generateToken(sessionId, expirationMillis);

        return new SessionCreateResponse(sessionId, token, true, mode);
    }

    @Transactional
    public void appendEvents(String sessionId, String eventsJson) {
        if (eventsJson == null) {
            throw new IllegalArgumentException("Events payload is required");
        }
        // Size is enforced in GzipRequestBodyReader (raw or decompressed). Keep a soft check for direct callers.
        int maxBytes = properties.getMaxEventPayloadBytes();
        if (maxBytes > 0 && eventsJson.length() > maxBytes) {
            throw new IllegalArgumentException("Events payload exceeds max size of " + maxBytes + " bytes");
        }

        if (!rateLimitService.allowEventAppend(sessionId)) {
            throw new RateLimitExceededException("Event append rate limit exceeded");
        }

        try {
            JsonNode eventsArray = objectMapper.readTree(eventsJson);
            if (!eventsArray.isArray()) {
                throw new IllegalArgumentException("Events payload must be an array");
            }

            int maxBatch = properties.getMaxEventBatchSize();
            if (maxBatch > 0 && eventsArray.size() > maxBatch) {
                throw new IllegalArgumentException("Events batch exceeds max size of " + maxBatch);
            }

            List<SessionEvent> events = new ArrayList<>(eventsArray.size());
            for (JsonNode eventNode : eventsArray) {
                long timestamp = eventNode.has("timestamp") ? eventNode.get("timestamp").asLong() : System.currentTimeMillis();
                try {
                    String eventData = objectMapper.writeValueAsString(eventNode);
                    events.add(new SessionEvent(sessionId, timestamp, eventData));
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize event node", e);
                }
            }

            if (!events.isEmpty()) {
                eventRepository.batchInsert(events);
                touchHeartbeatThrottled(sessionId);

                String channel = "session:live:" + sessionId;
                String payload = eventsJson;
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            redisTemplate.convertAndSend(channel, payload);
                        }
                    });
                } else {
                    redisTemplate.convertAndSend(channel, payload);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse events JSON", e);
            throw new IllegalArgumentException("Invalid JSON format");
        }
    }

    @Transactional
    public void heartbeat(String sessionId) {
        touchHeartbeatThrottled(sessionId);
    }

    @Transactional
    public void stopSession(String sessionId) {
        metadataRepository.findById(sessionId).ifPresent(metadata -> {
            metadata.stop();
            metadataRepository.save(metadata);
        });
    }

    private void touchHeartbeatThrottled(String sessionId) {
        int throttleSeconds = properties.getHeartbeatThrottleSeconds();
        metadataRepository.findById(sessionId).ifPresent(metadata -> {
            if (throttleSeconds > 0 && metadata.getUpdatedAt() != null) {
                ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(throttleSeconds);
                if (metadata.getUpdatedAt().isAfter(threshold)) {
                    return;
                }
            }
            metadata.heartbeat();
            metadataRepository.save(metadata);
        });
    }

    private String generateToken(String sessionId, long expirationMillis) {
        try {
            String payload = sessionId + ":" + expirationMillis;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

            String encodedSessionId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
            String encodedExpiration = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(String.valueOf(expirationMillis).getBytes(StandardCharsets.UTF_8));

            return encodedSessionId + "." + encodedExpiration + "." + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }
}
