package com.livescreenlog.app.controller;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class SsePushController {

    // Key: projectKey -> (userId -> emitters). Single-instance only; multi-node needs Redis fanout.
    private static final Map<String, Map<String, List<SseEmitter>>> EMITTERS = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL_CONNECTIONS = new AtomicInteger();

    private final ProjectRepository projectRepository;
    private final LiveScreenLogProperties properties;

    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam("projectKey") String projectKey,
            @RequestParam("userId") String userId
    ) {
        if (!isValidProjectKey(projectKey)) {
            throw new IllegalArgumentException("Invalid project key");
        }

        int maxTotal = Math.max(1, properties.getMaxPushSseTotal());
        int maxPerUser = Math.max(1, properties.getMaxPushSsePerUser());

        int total = TOTAL_CONNECTIONS.incrementAndGet();
        if (total > maxTotal) {
            TOTAL_CONNECTIONS.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Push SSE total connection limit exceeded");
        }

        Map<String, List<SseEmitter>> userMap = EMITTERS.computeIfAbsent(projectKey, k -> new ConcurrentHashMap<>());
        List<SseEmitter> list = userMap.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (list) {
            if (list.size() >= maxPerUser) {
                TOTAL_CONNECTIONS.decrementAndGet();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Push SSE per-user connection limit exceeded");
            }
        }

        SseEmitter emitter = new SseEmitter(1_800_000L);

        synchronized (list) {
            list.add(emitter);
        }

        log.info("SSE client connected. projectKey: {}, userId: {}", projectKey, userId);

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to LiveScreenLog signaling channel"));
        } catch (IOException e) {
            log.error("Failed to send init ping", e);
        }

        emitter.onCompletion(() -> removeEmitter(projectKey, userId, emitter));
        emitter.onTimeout(() -> removeEmitter(projectKey, userId, emitter));
        emitter.onError((ex) -> removeEmitter(projectKey, userId, emitter));

        return emitter;
    }

    @GetMapping("/active-terminals")
    public ResponseEntity<List<String>> getActiveTerminals(@RequestParam("projectKey") String projectKey) {
        if (!isValidProjectKey(projectKey)) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, List<SseEmitter>> userMap = EMITTERS.get(projectKey);
        if (userMap == null) {
            return ResponseEntity.ok(List.of());
        }
        List<String> activeUsers = new ArrayList<>();
        userMap.forEach((userId, emitterList) -> {
            synchronized (emitterList) {
                if (!emitterList.isEmpty()) {
                    activeUsers.add(userId);
                }
            }
        });
        return ResponseEntity.ok(activeUsers);
    }

    @PostMapping("/trigger-record")
    public ResponseEntity<Void> triggerRecord(
            @RequestParam("projectKey") String projectKey,
            @RequestParam("userId") String userId
    ) {
        if (!isValidProjectKey(projectKey)) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, List<SseEmitter>> userMap = EMITTERS.get(projectKey);
        if (userMap == null) {
            return ResponseEntity.notFound().build();
        }

        List<SseEmitter> emitterList = userMap.get(userId);
        if (emitterList == null || emitterList.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("Triggering real-time recording for projectKey: {}, userId: {}", projectKey, userId);

        List<SseEmitter> snapshot;
        synchronized (emitterList) {
            snapshot = new ArrayList<>(emitterList);
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().name("START_RECORDING").data("Trigger session record"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to push trigger signal, mark for clean: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        synchronized (emitterList) {
            emitterList.removeAll(deadEmitters);
        }
        return ResponseEntity.ok().build();
    }

    private boolean isValidProjectKey(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return false;
        }
        if (properties.getProjectKey() != null && properties.getProjectKey().equals(projectKey)) {
            return true;
        }
        return projectRepository.findByApiKey(projectKey).isPresent();
    }

    private void removeEmitter(String projectKey, String userId, SseEmitter emitter) {
        Map<String, List<SseEmitter>> userMap = EMITTERS.get(projectKey);
        if (userMap != null) {
            List<SseEmitter> list = userMap.get(userId);
            if (list != null) {
                boolean removed;
                synchronized (list) {
                    removed = list.remove(emitter);
                    if (list.isEmpty()) {
                        userMap.remove(userId);
                    }
                }
                if (removed) {
                    TOTAL_CONNECTIONS.decrementAndGet();
                }
            }
            if (userMap.isEmpty()) {
                EMITTERS.remove(projectKey);
            }
        }
    }
}
