package com.livescreenlog.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ForceTriggerService {

    private static final Duration TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redisTemplate;

    public void authorize(String projectKey, String userId) {
        redisTemplate.opsForValue().set(key(projectKey, userId), "1", TTL);
    }

    public boolean consume(String projectKey, String userId) {
        Boolean deleted = redisTemplate.delete(key(projectKey, userId));
        return Boolean.TRUE.equals(deleted);
    }

    private static String key(String projectKey, String userId) {
        return "force-trigger:" + projectKey + ":" + userId;
    }
}
