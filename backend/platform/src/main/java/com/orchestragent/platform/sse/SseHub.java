package com.orchestragent.platform.sse;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the open SSE connections of each workspace and fans events out to them. Emitters are removed when
 * they complete, time out, fail, or when a send to them fails. The caller of {@link #register} is
 * responsible for checking workspace membership first.
 */
public class SseHub {

    static final long DEFAULT_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    static final String HEARTBEAT_COMMENT = "heartbeat";

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final ConcurrentMap<UUID, Set<SseEmitter>> emittersByWorkspace = new ConcurrentHashMap<>();
    private final long timeoutMillis;
    private final LongFunction<SseEmitter> emitterFactory;

    public SseHub() {
        this(DEFAULT_TIMEOUT_MILLIS, SseEmitter::new);
    }

    SseHub(long timeoutMillis, LongFunction<SseEmitter> emitterFactory) {
        this.timeoutMillis = timeoutMillis;
        this.emitterFactory = emitterFactory;
    }

    /** Creates an emitter for the workspace and starts tracking it. */
    public SseEmitter register(UUID workspaceId) {
        SseEmitter emitter = emitterFactory.apply(timeoutMillis);
        emittersByWorkspace.computeIfAbsent(workspaceId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove = () -> remove(workspaceId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    /** Sends a named event to every connection of the workspace. Failing connections are dropped. */
    public void publish(UUID workspaceId, String eventType, Object payload) {
        Set<SseEmitter> emitters = emittersByWorkspace.get(workspaceId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(workspaceId, emitter, SseEmitter.event().name(eventType).data(payload));
        }
    }

    /** Keeps idle connections open through proxies. Runs every 25 seconds. */
    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeats() {
        emittersByWorkspace.forEach((workspaceId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                send(workspaceId, emitter, SseEmitter.event().comment(HEARTBEAT_COMMENT));
            }
        });
    }

    public int subscriberCount(UUID workspaceId) {
        Set<SseEmitter> emitters = emittersByWorkspace.get(workspaceId);
        return emitters == null ? 0 : emitters.size();
    }

    private void send(UUID workspaceId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping SSE connection of workspace {}: {}", workspaceId, e.toString());
            remove(workspaceId, emitter);
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException ignored) {
                // The connection is already gone; nothing more to clean up.
            }
        }
    }

    private void remove(UUID workspaceId, SseEmitter emitter) {
        emittersByWorkspace.computeIfPresent(workspaceId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
