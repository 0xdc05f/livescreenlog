package com.livescreenlog.app.service;

import com.livescreenlog.app.dto.StatsBucket;
import com.livescreenlog.app.dto.StatsOverviewResponse;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SessionMetadataRepository metadataRepository;
    private final UserProjectAccessService userProjectAccessService;

    public StatsOverviewResponse getOverview(String projectKey) {
        if (projectKey != null && !projectKey.isBlank() && !userProjectAccessService.hasAccessToProject(projectKey)) {
            return null;
        }
        String pk = (projectKey == null || projectKey.isBlank()) ? null : projectKey;
        List<String> allowed = userProjectAccessService.getAllowedProjectKeys();
        boolean unrestricted = allowed == null;
        if (!unrestricted && allowed.isEmpty()) {
            return empty(pk);
        }
        List<String> inList = unrestricted ? List.of("__all__") : allowed;
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoff = now.minusMinutes(5);
        long liveUsers = metadataRepository.countLiveUsers(cutoff, pk, unrestricted, inList);
        long liveSessions = metadataRepository.countLiveSessions(cutoff, pk, unrestricted, inList);

        ZonedDateTime hourTo = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime hourFrom = hourTo.minusHours(24);
        ZonedDateTime dayTo = now.plusDays(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime dayFrom = dayTo.minusDays(30);
        ZonedDateTime monthTo = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1);
        ZonedDateTime monthFrom = monthTo.minusMonths(12);

        List<StatsBucket> hours = fill(metadataRepository.usageByHour(hourFrom, hourTo, pk, unrestricted, inList),
                hourFrom, 24, ChronoUnit.HOURS, HOUR_FMT);
        List<StatsBucket> days = fill(metadataRepository.usageByDay(dayFrom, dayTo, pk, unrestricted, inList),
                dayFrom, 30, ChronoUnit.DAYS, DAY_FMT);
        List<StatsBucket> months = fill(metadataRepository.usageByMonth(monthFrom, monthTo, pk, unrestricted, inList),
                monthFrom, 12, ChronoUnit.MONTHS, MONTH_FMT);
        return new StatsOverviewResponse(pk, liveUsers, liveSessions, hours, days, months);
    }

    private StatsOverviewResponse empty(String pk) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime hourFrom = now.plusHours(1).truncatedTo(ChronoUnit.HOURS).minusHours(24);
        ZonedDateTime dayFrom = now.plusDays(1).truncatedTo(ChronoUnit.DAYS).minusDays(30);
        ZonedDateTime monthFrom = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1).minusMonths(12);
        return new StatsOverviewResponse(pk, 0, 0,
                fill(List.of(), hourFrom, 24, ChronoUnit.HOURS, HOUR_FMT),
                fill(List.of(), dayFrom, 30, ChronoUnit.DAYS, DAY_FMT),
                fill(List.of(), monthFrom, 12, ChronoUnit.MONTHS, MONTH_FMT));
    }

    private List<StatsBucket> fill(List<Object[]> rows, ZonedDateTime start, int n, ChronoUnit unit, DateTimeFormatter fmt) {
        Map<String, StatsBucket> map = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 3 || row[0] == null) continue;
                String b = String.valueOf(row[0]);
                long sessions = ((Number) row[1]).longValue();
                long users = ((Number) row[2]).longValue();
                map.put(b, new StatsBucket(b, sessions, users));
            }
        }
        List<StatsBucket> out = new ArrayList<>(n);
        ZonedDateTime t = start;
        for (int i = 0; i < n; i++) {
            String key = t.format(fmt);
            out.add(map.getOrDefault(key, new StatsBucket(key, 0, 0)));
            t = t.plus(1, unit);
        }
        return out;
    }
}