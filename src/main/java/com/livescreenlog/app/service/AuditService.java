package com.livescreenlog.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livescreenlog.app.domain.AuditLog;
import com.livescreenlog.app.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String targetSessionId, String projectKey, java.util.Map<String, Object> details) {
        String actor = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getName() != null
                && !"anonymousUser".equals(String.valueOf(auth.getName()))) {
            actor = auth.getName();
        }
        String detailsJson = null;
        try {
            if (details != null) {
                detailsJson = objectMapper.writeValueAsString(details);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize audit details for action={}", action, e);
        }
        AuditLog entry = AuditLog.builder()
                .actorUsername(actor)
                .action(action)
                .targetSessionId(targetSessionId)
                .projectKey(projectKey)
                .details(detailsJson)
                .build();
        auditLogRepository.save(entry);
        log.info("Audit: action='{}' actor='{}' target='{}' project='{}'", action, actor, targetSessionId, projectKey);
    }
}
