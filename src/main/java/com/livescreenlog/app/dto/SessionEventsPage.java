package com.livescreenlog.app.dto;

import com.livescreenlog.app.domain.SessionEvent;

import java.util.List;

public record SessionEventsPage(
        List<SessionEvent> events,
        Long nextAfterId,
        boolean hasMore
) {}
