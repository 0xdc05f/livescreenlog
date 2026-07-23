package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final LiveScreenLogProperties properties;

    public boolean tryConsume(String bucket, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        String key = "ratelimit:" + bucket + ":" + (System.currentTimeMillis() / 60_000);
        Long count = redisTemplate.execute(INCR_WITH_EXPIRE, List.of(key), "120");
        return count == null || count <= limitPerMinute;
    }

    public boolean allowSessionCreate(String clientKey) {
        return tryConsume("create:" + clientKey, properties.getSessionCreatePerMinute());
    }

    public boolean allowEventAppend(String sessionId) {
        return tryConsume("events:" + sessionId, properties.getEventAppendPerMinute());
    }
}
