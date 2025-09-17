package com.example.orgmanager.web;

import com.example.orgmanager.service.OrganizationEventPublisher;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SseEmitter> subscribeOrganizations(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(publisher.subscribe());
    }
}
