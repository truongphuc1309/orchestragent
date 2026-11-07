package com.orchestragent.platform.sse;

import java.io.IOException;
import java.util.Objects;
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
 *
 * <p>A connection may belong to a user; then {@link #publishToUser} reaches it too, for events that concern one
 * person only (their inbox). Events for the whole workspace ({@link #publish}) reach every connection.
 */
public class SseHub {

    static final long DEFAULT_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /**
     * Name of the keep-alive event. It is a real event, not an SSE comment, because browsers hide comments from
     * JavaScript: clients use it to notice a connection that died without telling them.
     */
    public static final String HEARTBEAT_EVENT = "heartbeat";

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    /** One open connection, and the user it was opened for (null when unknown). */
    private record Connection(SseEmitter emitter, UUID userId) {
    }

    private final ConcurrentMap<UUID, Set<Connection>> connectionsByWorkspace = new ConcurrentHashMap<>();
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
        return register(workspaceId, null);
    }

    /** Creates an emitter for a user in the workspace and starts tracking it. */
    public SseEmitter register(UUID workspaceId, UUID userId) {
        SseEmitter emitter = emitterFactory.apply(timeoutMillis);
        Connection connection = new Connection(emitter, userId);
        connectionsByWorkspace.computeIfAbsent(workspaceId, id -> ConcurrentHashMap.newKeySet()).add(connection);
        Runnable remove = () -> remove(workspaceId, connection);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    /** Sends a named event to every connection of the workspace. Failing connections are dropped. */
    public void publish(UUID workspaceId, String eventType, Object payload) {
        Set<Connection> connections = connectionsByWorkspace.get(workspaceId);
        if (connections == null) {
            return;
        }
        for (Connection connection : connections) {
            send(workspaceId, connection, SseEmitter.event().name(eventType).data(payload));
        }
    }

    /** Sends a named event only to the connections the user opened in the workspace. */
    public void publishToUser(UUID workspaceId, UUID userId, String eventType, Object payload) {
        Objects.requireNonNull(userId, "userId");
        Set<Connection> connections = connectionsByWorkspace.get(workspaceId);
        if (connections == null) {
            return;
        }
        for (Connection connection : connections) {
            if (userId.equals(connection.userId())) {
                send(workspaceId, connection, SseEmitter.event().name(eventType).data(payload));
            }
        }
    }

    /** Keeps idle connections open through proxies and lets clients detect dead ones. Runs every 25 seconds. */
    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeats() {
        connectionsByWorkspace.forEach((workspaceId, connections) -> {
            for (Connection connection : connections) {
                send(workspaceId, connection, SseEmitter.event().name(HEARTBEAT_EVENT).data("{}"));
            }
        });
    }

    public int subscriberCount(UUID workspaceId) {
        Set<Connection> connections = connectionsByWorkspace.get(workspaceId);
        return connections == null ? 0 : connections.size();
    }

    private void send(UUID workspaceId, Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping SSE connection of workspace {}: {}", workspaceId, e.toString());
            remove(workspaceId, connection);
            try {
                connection.emitter().completeWithError(e);
            } catch (RuntimeException ignored) {
                // The connection is already gone; nothing more to clean up.
            }
        }
    }

    private void remove(UUID workspaceId, Connection connection) {
        connectionsByWorkspace.computeIfPresent(workspaceId, (id, connections) -> {
            connections.remove(connection);
            return connections.isEmpty() ? null : connections;
        });
    }
}
