package com.example.orgmanager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class OrganizationEventPublisher {
    private static final long TIMEOUT_MINUTES = 5L;
    private static final Logger LOGGER =
            LoggerFactory.getLogger(OrganizationEventPublisher.class);
    private static final long TIMEOUT_MS =
            Duration.ofMinutes(TIMEOUT_MINUTES).toMillis();

    private final boolean heartbeatEnabled;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public OrganizationEventPublisher(
            @Value("${app.sse.heartbeat-enabled:false}")
            boolean heartbeatEnabled) {
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
            safeComplete(emitter, e);
        });

        return emitter;
    }

    public void broadcast(String type, Integer id) {
        if (emitters.isEmpty()) {
            return;
        }
        try {
            var event = new OrganizationEvent(type, id);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(
                            SseEmitter.event()
                                    .name("org")
                                    .data(event));
                } catch (Exception e) {
                    LOGGER.debug(
                            "Removing broken SSE emitter: {}",
                            e.getMessage());
                    emitters.remove(emitter);
                    safeComplete(emitter, e);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Broadcast failed", t);
        }
    }

    // Periodic ping keeps SSE connection from idling out via proxies
    @Scheduled(fixedRateString = "${app.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        if (!heartbeatEnabled || emitters.isEmpty()) {
            return;
        }
        try {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(
                            SseEmitter.event()
                                    .name("ping")
                                    .data(
                                            Instant.now().toString(),
                                            MediaType.TEXT_PLAIN));
                } catch (Exception e) {
                    emitters.remove(emitter);
                    safeComplete(emitter, e);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Heartbeat tick error", t);
        }
    }

    public record OrganizationEvent(String type, Integer id) {
    }

    private void safeComplete(SseEmitter emitter) {
        safeComplete(emitter, null);
    }

    private void safeComplete(SseEmitter emitter, Throwable cause) {
        try {
            if (cause == null) {
                emitter.complete();
            } else {
                LOGGER.debug("Completing SSE emitter with error", cause);
                emitter.completeWithError(cause);
            }
        } catch (Throwable ex) {
            LOGGER.debug("Failed to complete SSE emitter", ex);
        }
    }

}
