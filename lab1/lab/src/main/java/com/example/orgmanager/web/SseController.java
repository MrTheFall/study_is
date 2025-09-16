package com.example.orgmanager.web;

import com.example.orgmanager.service.OrganizationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {
    private final OrganizationEventPublisher publisher;

    public SseController(OrganizationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(path = "/events/organizations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeOrganizations() {
        return publisher.subscribe();
    }
}

