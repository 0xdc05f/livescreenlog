package com.livescreenlog.app.controller;

import com.livescreenlog.app.domain.SessionEvent;
import com.livescreenlog.app.dto.SessionEventsPage;
import com.livescreenlog.app.dto.SessionResponse;
import com.livescreenlog.app.service.SessionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionReadController {

    private final SessionReadService readService;

    @GetMapping
    public ResponseEntity<Page<SessionResponse>> searchSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) String query,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SessionResponse> result = readService.searchSessions(
                startDate, endDate, userId, source, status, projectKey, query, pageable);
        return ResponseEntity.ok(result);
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
        SessionResponse response = readService.getSessionDetails(id);
        if (response == null) {
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
}
