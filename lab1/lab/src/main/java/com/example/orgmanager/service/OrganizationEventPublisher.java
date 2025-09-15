package com.example.orgmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
 

@Component
public class OrganizationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrganizationEventPublisher.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 минут

    private final boolean heartbeatEnabled;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public OrganizationEventPublisher(@Value("${app.sse.heartbeat-enabled:false}") boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            safeComplete(emitter);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            safeComplete(emitter);
        });


        return emitter;
    }

    public void broadcast(String type, Integer id) {
        if (emitters.isEmpty()) return;
        try {
            var event = new OrganizationEvent(type, id);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("org").data(event));
                } catch (Exception e) {
                    log.debug("Removing broken SSE emitter: {}", e.getMessage());
                    emitters.remove(emitter);
                    safeCompleteWithError(emitter, e);
                }
            }
        } catch (Throwable t) {
            log.debug("Broadcast failed: {}", t.toString());
        }
    }

    // Периодический ping, чтобы соединение не простаивало и не закрывалось прокси
    @Scheduled(fixedRateString = "${app.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        if (!heartbeatEnabled || emitters.isEmpty()) return;
        try {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data(Instant.now().toString(), MediaType.TEXT_PLAIN));
                } catch (Exception e) {
                    emitters.remove(emitter);
                    safeComplete(emitter);
                }
            }
        } catch (Throwable t) {
            log.debug("Heartbeat tick error: {}", t.toString());
        }
    }

    public record OrganizationEvent(String type, Integer id) {}

    private void safeComplete(SseEmitter emitter) {
        try { emitter.complete(); } catch (Throwable ignored) {}
    }

    private void safeCompleteWithError(SseEmitter emitter, Throwable ex) {
        try { emitter.completeWithError(ex); } catch (Throwable ignored) {}
    }
}
