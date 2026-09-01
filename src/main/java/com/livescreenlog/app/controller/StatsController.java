package com.livescreenlog.app.controller;

import com.livescreenlog.app.dto.StatsOverviewResponse;
import com.livescreenlog.app.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ResponseEntity<StatsOverviewResponse> overview(@RequestParam(required = false) String projectKey) {
        String pk = (projectKey == null || projectKey.isBlank()) ? null : projectKey;
        StatsOverviewResponse body = statsService.getOverview(pk);
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(body);
    }
}