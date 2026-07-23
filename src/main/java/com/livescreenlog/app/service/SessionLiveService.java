package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionLiveService {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final LiveScreenLogProperties properties;

    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000L;
    private static final long PING_INTERVAL_SECONDS = 15L;

    private final ScheduledExecutorService pingScheduler =
            Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
                Thread t = new Thread(r, "sse-ping");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, AtomicInteger> subscriberCounts = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String sessionId) {
        int maxPerSession = Math.max(1, properties.getMaxLiveSsePerSession());
        AtomicInteger count = subscriberCounts.computeIfAbsent(sessionId, k -> new AtomicInteger());
        int current = count.incrementAndGet();
        if (current > maxPerSession) {
            count.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Live SSE subscriber limit exceeded for session");
        }

        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                emitter.send(SseEmitter.event().name("message").data(payload));
            } catch (IOException e) {
                log.error("Failed to send SSE for session {}", sessionId, e);
                emitter.completeWithError(e);
            }
        };

        ChannelTopic topic = new ChannelTopic("session:live:" + sessionId);
        redisMessageListenerContainer.addMessageListener(listener, topic);

        ScheduledFuture<?> pingFuture = pingScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Runnable cleanup = () -> {
            log.info("Cleaning up SSE subscription for session {}", sessionId);
            pingFuture.cancel(false);
            redisMessageListenerContainer.removeMessageListener(listener, topic);
            AtomicInteger c = subscriberCounts.get(sessionId);
            if (c != null && c.decrementAndGet() <= 0) {
                subscriberCounts.remove(sessionId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE connected for session " + sessionId));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public int subscriberCount(String sessionId) {
        AtomicInteger count = subscriberCounts.get(sessionId);
        return count == null ? 0 : count.get();
    }
}
