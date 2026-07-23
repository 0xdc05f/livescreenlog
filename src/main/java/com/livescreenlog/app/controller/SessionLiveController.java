package com.livescreenlog.app.controller;

import com.livescreenlog.app.service.SessionLiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionLiveController {

    private final SessionLiveService sessionLiveService;

    @GetMapping(value = "/{id}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveTailing(@PathVariable String id) {
        return sessionLiveService.subscribe(id);
    }
}
