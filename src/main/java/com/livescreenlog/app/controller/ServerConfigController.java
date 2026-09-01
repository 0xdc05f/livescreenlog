package com.livescreenlog.app.controller;

import com.livescreenlog.app.service.ServerConfigService;
import com.livescreenlog.app.service.SessionTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ServerConfigController {

    private final ServerConfigService configService;
    private final SessionTimeoutService sessionTimeoutService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(configService.getAllSafe());
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> set(@PathVariable("key") String key, @RequestBody Map<String, String> body) {
        if (!configService.isSafeKey(key)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Key not editable via UI"));
        }
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'value'"));
        }
        try {
            configService.set(key, value);
            return ResponseEntity.ok(Map.of("key", key, "value", value));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/retention/purge")
    public ResponseEntity<Map<String, Object>> purge() {
        int deleted = sessionTimeoutService.manualPurge();
        return ResponseEntity.ok(Map.of("deleted", deleted, "message", "Purged " + deleted + " sessions"));
    }
}
