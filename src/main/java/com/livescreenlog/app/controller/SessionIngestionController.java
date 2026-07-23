package com.livescreenlog.app.controller;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.dto.SessionCreateRequest;
import com.livescreenlog.app.dto.SessionCreateResponse;
import com.livescreenlog.app.service.SessionIngestionService;
import com.livescreenlog.app.web.GzipRequestBodyReader;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionIngestionController {

    private final SessionIngestionService ingestionService;
    private final LiveScreenLogProperties properties;

    @PostMapping("/sessions")
    public ResponseEntity<SessionCreateResponse> createSession(
            @RequestBody SessionCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientKey = clientKey(httpRequest, request.effectiveProjectKey());
        return ResponseEntity.ok(ingestionService.createSession(request, clientKey));
    }

    @PostMapping("/events")
    public ResponseEntity<Void> appendEvents(
            @AuthenticationPrincipal String sessionId,
            HttpServletRequest request
    ) throws IOException {
        if (sessionId == null) {
            return ResponseEntity.status(401).build();
        }
        String eventsJson = GzipRequestBodyReader.readUtf8(request, properties.getMaxEventPayloadBytes());
        ingestionService.appendEvents(sessionId, eventsJson);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal String sessionId) {
        if (sessionId == null) {
            return ResponseEntity.status(401).build();
        }
        ingestionService.heartbeat(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopSession(@AuthenticationPrincipal String sessionId) {
        if (sessionId == null) {
            return ResponseEntity.status(401).build();
        }
        ingestionService.stopSession(sessionId);
        return ResponseEntity.ok().build();
    }

    private static String clientKey(HttpServletRequest request, String projectKey) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return (projectKey != null ? projectKey : "unknown") + ":" + (ip != null ? ip : "unknown");
    }
}
