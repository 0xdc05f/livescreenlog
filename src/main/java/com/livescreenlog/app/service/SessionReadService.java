package com.livescreenlog.app.service;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.domain.SessionEvent;
import com.livescreenlog.app.domain.SessionMetadata;
import com.livescreenlog.app.dto.SessionEventsPage;
import com.livescreenlog.app.dto.SessionResponse;
import com.livescreenlog.app.repository.ProjectRepository;
import com.livescreenlog.app.repository.SessionEventRepository;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionReadService {

    private static final long PROJECT_CACHE_TTL_MS = 30_000L;

    private final SessionMetadataRepository metadataRepository;
    private final SessionEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final LiveScreenLogProperties properties;

    private final Map<String, String> projectNameCache = new ConcurrentHashMap<>();
    private final AtomicLong projectCacheLoadedAt = new AtomicLong(0L);

    public Page<SessionResponse> searchSessions(
            ZonedDateTime startDate, ZonedDateTime endDate,
            String userId, String source, String status, String projectKey, String queryVal,
            Pageable pageable) {

        refreshProjectCacheIfNeeded();

        Specification<SessionMetadata> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (userId != null && !userId.isBlank()) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (projectKey != null && !projectKey.isBlank()) {
                predicates.add(cb.equal(root.get("projectKey"), projectKey));
            }
            if (queryVal != null && !queryVal.isBlank()) {
                String trimmed = queryVal.trim();
                String lower = trimmed.toLowerCase();
                // Prefer prefix/contains patterns that work with pg_trgm gin indexes on lower(col)
                String pattern = "%" + lower + "%";

                List<String> matchedProjectKeys = projectNameCache.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue().toLowerCase().contains(lower))
                        .map(Map.Entry::getKey)
                        .toList();

                List<Predicate> queryPredicates = new ArrayList<>();
                // Exact userId match is cheap when operators paste full employee id
                queryPredicates.add(cb.equal(root.get("userId"), trimmed));
                queryPredicates.add(cb.like(cb.lower(root.get("userId")), pattern));
                queryPredicates.add(cb.like(cb.lower(root.get("source")), pattern));
                // distinctId (UA) only when query looks browser/os related or long enough
                if (lower.length() >= 4) {
                    queryPredicates.add(cb.like(cb.lower(root.get("distinctId")), pattern));
                }

                if (!matchedProjectKeys.isEmpty()) {
                    queryPredicates.add(root.get("projectKey").in(matchedProjectKeys));
                }

                predicates.add(cb.or(queryPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return metadataRepository.findAll(spec, pageable)
                .map(m -> SessionResponse.from(m, projectNameCache.get(m.getProjectKey())));
    }

    public SessionResponse getSessionDetails(String sessionId) {
        refreshProjectCacheIfNeeded();
        return metadataRepository.findById(sessionId)
                .map(m -> SessionResponse.from(m, projectNameCache.get(m.getProjectKey())))
                .orElse(null);
    }

    public List<SessionEvent> getSessionEvents(String sessionId) {
        int cap = Math.max(1, properties.getMaxEventFullDumpSize());
        return eventRepository.findBySessionIdPaged(sessionId, null, cap);
    }

    public SessionEventsPage getSessionEventsPage(String sessionId, Long afterId, Integer limit) {
        int defaultSize = Math.max(1, properties.getDefaultEventPageSize());
        int maxSize = Math.max(defaultSize, properties.getMaxEventPageSize());
        int pageSize = limit == null ? defaultSize : Math.min(Math.max(limit, 1), maxSize);
        List<SessionEvent> batch = eventRepository.findBySessionIdPaged(sessionId, afterId, pageSize + 1);
        boolean hasMore = batch.size() > pageSize;
        List<SessionEvent> events = hasMore ? batch.subList(0, pageSize) : batch;
        Long nextAfterId = events.isEmpty() ? null : events.get(events.size() - 1).id();
        return new SessionEventsPage(events, nextAfterId, hasMore);
    }

    private void refreshProjectCacheIfNeeded() {
        long now = System.currentTimeMillis();
        long loadedAt = projectCacheLoadedAt.get();
        if (now - loadedAt < PROJECT_CACHE_TTL_MS && loadedAt > 0) {
            return;
        }
        if (!projectCacheLoadedAt.compareAndSet(loadedAt, now)) {
            return;
        }
        Map<String, String> fresh = new ConcurrentHashMap<>();
        projectRepository.findAll().forEach(p -> fresh.put(p.getApiKey(), p.getName()));
        projectNameCache.clear();
        projectNameCache.putAll(fresh);
    }
}
