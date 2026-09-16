package com.livescreenlog.app.controller;

import com.livescreenlog.app.service.SessionLiveService;
import com.livescreenlog.app.service.UserProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionLiveController {

    private final SessionLiveService sessionLiveService;
    private final UserProjectAccessService userProjectAccessService;

    @GetMapping(value = "/{id}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveTailing(@PathVariable String id) {
        if (!userProjectAccessService.hasAccessToSession(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return sessionLiveService.subscribe(id);
    }
}
