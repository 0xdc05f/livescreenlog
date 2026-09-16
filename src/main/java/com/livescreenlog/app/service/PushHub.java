package com.livescreenlog.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushHub {

    public static final String TRIGGER_CHANNEL = "push:trigger";

    private final Map<String, Map<String, List<SseEmitter>>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger();

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void subscribe() {
        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(payload);
                dispatchLocal(node.path("projectKey").asText(), node.path("userId").asText());
            } catch (Exception e) {
                log.warn("Failed to handle push trigger fanout: {}", e.getMessage());
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(TRIGGER_CHANNEL));
    }

    public SseEmitter connect(String projectKey, String userId, int maxTotal, int maxPerUser) {
        int total = totalConnections.incrementAndGet();
        if (total > maxTotal) {
            totalConnections.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Push SSE total connection limit exceeded");
        }

        List<SseEmitter> list = emitters
                .computeIfAbsent(projectKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (list) {
            if (list.size() >= maxPerUser) {
                totalConnections.decrementAndGet();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Push SSE per-user connection limit exceeded");
            }
        }

        SseEmitter emitter = new SseEmitter(1_800_000L);
        synchronized (list) {
            list.add(emitter);
        }
        markOnline(projectKey, userId);

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to LiveScreenLog signaling channel"));
        } catch (IOException e) {
            log.error("Failed to send init ping", e);
        }

        emitter.onCompletion(() -> removeEmitter(projectKey, userId, emitter));
        emitter.onTimeout(() -> removeEmitter(projectKey, userId, emitter));
        emitter.onError(ex -> removeEmitter(projectKey, userId, emitter));
        return emitter;
    }

    public List<String> activeUsers(String projectKey) {
        try {
            var members = redisTemplate.opsForSet().members(usersKey(projectKey));
            if (members != null && !members.isEmpty()) {
                return members.stream().sorted().toList();
            }
        } catch (Exception e) {
            log.warn("Redis presence read failed, falling back to local emitters: {}", e.getMessage());
        }
        Map<String, List<SseEmitter>> userMap = emitters.get(projectKey);
        if (userMap == null) {
            return List.of();
        }
        List<String> active = new ArrayList<>();
        userMap.forEach((userId, list) -> {
            synchronized (list) {
                if (!list.isEmpty()) {
                    active.add(userId);
                }
            }
        });
        return active;
    }

    public boolean isOnline(String projectKey, String userId) {
        return activeUsers(projectKey).contains(userId) || hasLocal(projectKey, userId);
    }

    public boolean trigger(String projectKey, String userId) {
        if (!isOnline(projectKey, userId)) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "projectKey", projectKey,
                    "userId", userId
            ));
            redisTemplate.convertAndSend(TRIGGER_CHANNEL, payload);
            return true;
        } catch (Exception e) {
            log.warn("Redis trigger publish failed, dispatching locally: {}", e.getMessage());
            return dispatchLocal(projectKey, userId);
        }
    }

    private boolean hasLocal(String projectKey, String userId) {
        Map<String, List<SseEmitter>> userMap = emitters.get(projectKey);
        if (userMap == null) {
            return false;
        }
        List<SseEmitter> list = userMap.get(userId);
        if (list == null) {
            return false;
        }
        synchronized (list) {
            return !list.isEmpty();
        }
    }

    private boolean dispatchLocal(String projectKey, String userId) {
        if (projectKey == null || userId == null) {
            return false;
        }
        Map<String, List<SseEmitter>> userMap = emitters.get(projectKey);
        if (userMap == null) {
            return false;
        }
        List<SseEmitter> emitterList = userMap.get(userId);
        if (emitterList == null) {
            return false;
        }
        List<SseEmitter> snapshot;
        synchronized (emitterList) {
            if (emitterList.isEmpty()) {
                return false;
            }
            snapshot = new ArrayList<>(emitterList);
        }
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().name("START_RECORDING").data("Trigger session record"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to push trigger signal: {}", e.getMessage());
                dead.add(emitter);
            }
        }
        synchronized (emitterList) {
            emitterList.removeAll(dead);
        }
        return true;
    }

    private void markOnline(String projectKey, String userId) {
        try {
            Long n = redisTemplate.opsForValue().increment(countKey(projectKey, userId));
            redisTemplate.expire(countKey(projectKey, userId), Duration.ofHours(6));
            if (n != null && n == 1L) {
                redisTemplate.opsForSet().add(usersKey(projectKey), userId);
            }
        } catch (Exception e) {
            log.warn("Redis presence add failed: {}", e.getMessage());
        }
    }

    private void markOffline(String projectKey, String userId) {
        try {
            Long n = redisTemplate.opsForValue().decrement(countKey(projectKey, userId));
            if (n == null || n <= 0) {
                redisTemplate.delete(countKey(projectKey, userId));
                redisTemplate.opsForSet().remove(usersKey(projectKey), userId);
            }
        } catch (Exception e) {
            log.warn("Redis presence remove failed: {}", e.getMessage());
        }
    }

    private void removeEmitter(String projectKey, String userId, SseEmitter emitter) {
        Map<String, List<SseEmitter>> userMap = emitters.get(projectKey);
        if (userMap == null) {
            return;
        }
        List<SseEmitter> list = userMap.get(userId);
        if (list == null) {
            return;
        }
        boolean removed;
        synchronized (list) {
            removed = list.remove(emitter);
            if (list.isEmpty()) {
                userMap.remove(userId);
            }
        }
        if (removed) {
            totalConnections.decrementAndGet();
            markOffline(projectKey, userId);
        }
        if (userMap.isEmpty()) {
            emitters.remove(projectKey);
        }
    }

    private static String usersKey(String projectKey) {
        return "push:users:" + projectKey;
    }

    private static String countKey(String projectKey, String userId) {
        return "push:count:" + projectKey + ":" + userId;
    }
}
