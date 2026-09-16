package com.livescreenlog.app.controller;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.repository.ProjectRepository;
import com.livescreenlog.app.service.ForceTriggerService;
import com.livescreenlog.app.service.PushHub;
import com.livescreenlog.app.service.ServerConfigService;
import com.livescreenlog.app.service.UserProjectAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class SsePushController {

    private final ProjectRepository projectRepository;
    private final LiveScreenLogProperties properties;
    private final ServerConfigService serverConfigService;
    private final UserProjectAccessService userProjectAccessService;
    private final ForceTriggerService forceTriggerService;
    private final PushHub pushHub;

    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam("projectKey") String projectKey,
            @RequestParam("userId") String userId
    ) {
        if (!isValidProjectKey(projectKey)) {
            throw new IllegalArgumentException("Invalid project key");
        }
        log.info("SSE client connected. projectKey: {}, userId: {}", projectKey, userId);
        return pushHub.connect(
                projectKey,
                userId,
                Math.max(1, properties.getMaxPushSseTotal()),
                Math.max(1, properties.getMaxPushSsePerUser())
        );
    }

    @GetMapping("/active-terminals")
    public ResponseEntity<List<String>> getActiveTerminals(@RequestParam("projectKey") String projectKey) {
        if (!isValidProjectKey(projectKey) || !userProjectAccessService.canManageProject(projectKey)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pushHub.activeUsers(projectKey));
    }

    @PostMapping("/trigger-record")
    public ResponseEntity<Void> triggerRecord(
            @RequestParam("projectKey") String projectKey,
            @RequestParam("userId") String userId
    ) {
        if (!isValidProjectKey(projectKey) || !userProjectAccessService.canManageProject(projectKey)) {
            return ResponseEntity.notFound().build();
        }
        if (!pushHub.isOnline(projectKey, userId)) {
            return ResponseEntity.notFound().build();
        }
        forceTriggerService.authorize(projectKey, userId);
        if (!pushHub.trigger(projectKey, userId)) {
            return ResponseEntity.notFound().build();
        }
        log.info("Triggering real-time recording for projectKey: {}, userId: {}", projectKey, userId);
        return ResponseEntity.ok().build();
    }

    private boolean isValidProjectKey(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return false;
        }
        String globalKey = serverConfigService.getEffectiveProjectKey();
        if (globalKey != null && globalKey.equals(projectKey)) {
            return true;
        }
        return projectRepository.findByApiKey(projectKey).isPresent();
    }
}
