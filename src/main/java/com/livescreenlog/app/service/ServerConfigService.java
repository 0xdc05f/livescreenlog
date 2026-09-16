package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.domain.ServerConfig;
import com.livescreenlog.app.repository.ServerConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ServerConfigService {

    private final ServerConfigRepository repo;
    private final LiveScreenLogProperties properties;

    // Keys that are safe to override from UI / DB
    private static final Set<String> SAFE_KEYS = Set.of(
        "project-key",
        "allowed-capture-origins",
        "retention-days"
    );

    public Map<String, String> getAllSafe() {
        Map<String, String> map = new LinkedHashMap<>();
        // DB overrides take precedence
        repo.findAll().forEach(c -> map.put(c.getKey(), c.getValue()));

        // Fill defaults from env/properties if not overridden
        if (!map.containsKey("project-key")) {
            String v = properties.getProjectKey();
            if (v != null) map.put("project-key", v);
        }
        if (!map.containsKey("allowed-capture-origins")) {
            List<String> origins = properties.getAllowedCaptureOrigins();
            if (origins != null && !origins.isEmpty()) {
                map.put("allowed-capture-origins", String.join(",", origins));
            }
        }
        if (!map.containsKey("retention-days")) {
            map.put("retention-days", String.valueOf(properties.getRetentionDays()));
        }
        return map;
    }

    public Optional<String> get(String key) {
        if (!SAFE_KEYS.contains(key)) return Optional.empty();
        return repo.findById(key).map(ServerConfig::getValue)
            .or(() -> Optional.ofNullable(defaultFromProperties(key)));
    }

    private String defaultFromProperties(String key) {
        return switch (key) {
            case "project-key" -> properties.getProjectKey();
            case "allowed-capture-origins" -> {
                List<String> o = properties.getAllowedCaptureOrigins();
                yield (o == null || o.isEmpty()) ? "*" : String.join(",", o);
            }
            case "retention-days" -> String.valueOf(properties.getRetentionDays());
            default -> null;
        };
    }

    @Transactional
    public void set(String key, String value) {
        if (!SAFE_KEYS.contains(key)) {
            throw new IllegalArgumentException("Key not allowed to be set via UI: " + key);
        }
        if ("allowed-capture-origins".equals(key) && value != null
                && Arrays.stream(value.split(",")).map(String::trim).anyMatch("*"::equals)) {
            throw new IllegalArgumentException("Wildcard capture origins are not allowed");
        }
        ServerConfig cfg = repo.findById(key).orElseGet(() -> new ServerConfig(key, value));
        cfg.setValue(value);
        cfg.setUpdatedAt(ZonedDateTime.now());
        repo.save(cfg);
    }

    // Effective getters used by the app
    public String getEffectiveProjectKey() {
        return get("project-key").orElse(properties.getProjectKey());
    }

    public List<String> getEffectiveAllowedCaptureOrigins() {
        String raw = get("allowed-capture-origins").orElse(null);
        if (raw == null || raw.isBlank()) {
            List<String> fromProps = properties.getAllowedCaptureOrigins();
            return (fromProps == null || fromProps.isEmpty()) ? List.of("*") : fromProps;
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public int getEffectiveRetentionDays() {
        try {
            String v = get("retention-days").orElse(String.valueOf(properties.getRetentionDays()));
            return Integer.parseInt(v);
        } catch (Exception e) {
            return properties.getRetentionDays();
        }
    }

    public boolean isSafeKey(String key) {
        return SAFE_KEYS.contains(key);
    }
}
