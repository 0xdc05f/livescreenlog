package com.livescreenlog.app.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionEvent(
        Long id,
        String sessionId,
        long timestamp,
        @JsonRawValue String eventData
) {
    public SessionEvent(String sessionId, long timestamp, String eventData) {
        this(null, sessionId, timestamp, eventData);
    }
}
