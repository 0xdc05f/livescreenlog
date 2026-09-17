package com.livescreenlog.app.controller;

import com.livescreenlog.app.domain.SessionEvent;
import com.livescreenlog.app.dto.SessionEventsPage;
import com.livescreenlog.app.dto.SessionResponse;
import com.livescreenlog.app.service.SessionIngestionService;
import com.livescreenlog.app.service.SessionReadService;
import com.livescreenlog.app.service.UserProjectAccessService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionReadController {

    private final SessionReadService readService;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final SessionIngestionService ingestionService;
    private final UserProjectAccessService userProjectAccessService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<SessionResponse>> searchSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime updatedAfter,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SessionResponse> result = readService.searchSessions(
                startDate, endDate, userId, source, status, projectKey, query, updatedAfter, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<SessionResponse>> getRecommendedSessions(@RequestParam(defaultValue = "6") int limit) {
        List<SessionResponse> result = readService.getRecommendedSessions(limit);
        return ResponseEntity.ok(result);
    }

    /**
     * Real-time session list updates via Server-Sent Events.
     * Clients connect here and receive "session_created" events as they happen.
     */
    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveSessionList() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(payload);
                String projectKey = node.path("projectKey").asText(null);
                if (projectKey != null && !projectKey.isBlank()
                        && !userProjectAccessService.hasAccessToProject(projectKey)) {
                    return;
                }
                emitter.send(SseEmitter.event().name("session_created").data(payload));
            } catch (Exception e) {
                emitter.complete();
            }
        };

        ChannelTopic topic = new ChannelTopic("session:created");
        redisMessageListenerContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> redisMessageListenerContainer.removeMessageListener(listener, topic);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("session list live connected"));
        } catch (Exception ignored) {}

        return emitter;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSessionDetails(@PathVariable String id) {
        SessionResponse response = readService.getSessionDetails(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<?> getSessionEvents(
            @PathVariable String id,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "true") boolean paged
    ) {
        if (!readService.canReadSession(id)) {
            return ResponseEntity.notFound().build();
        }

        if (paged) {
            SessionEventsPage page = readService.getSessionEventsPage(id, afterId, limit);
            return ResponseEntity.ok(page);
        }

        // Legacy full dump — still hard-capped server-side
        List<SessionEvent> events = readService.getSessionEvents(id);
        return ResponseEntity.ok(events);
    }

    // security: admin protected via SecurityConfig (hasAnyRole ADMIN/SUPER_ADMIN on /api/sessions/**)
    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> forceStopSession(@PathVariable String id) {
        if (!userProjectAccessService.canManageSession(id)) {
            return ResponseEntity.notFound().build();
        }
        ingestionService.stopSession(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        if (!userProjectAccessService.canManageSession(id)) {
            return ResponseEntity.notFound().build();
        }
        ingestionService.deleteSession(id);
        return ResponseEntity.ok().build();
    }
}
