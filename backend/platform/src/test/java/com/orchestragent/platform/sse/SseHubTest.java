package com.orchestragent.platform.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseHubTest {

    private final UUID workspace = UUID.randomUUID();
    private final List<RecordingEmitter> created = new ArrayList<>();
    private final SseHub hub = new SseHub(1000, timeout -> {
        RecordingEmitter emitter = new RecordingEmitter(timeout);
        created.add(emitter);
        return emitter;
    });

    @Test
    void registerTracksEmittersPerWorkspace() {
        hub.register(workspace);
        hub.register(workspace);
        hub.register(UUID.randomUUID());

        assertThat(hub.subscriberCount(workspace)).isEqualTo(2);
    }

    @Test
    void publishReachesOnlyEmittersOfThatWorkspace() {
        hub.register(workspace);
        hub.register(UUID.randomUUID());

        hub.publish(workspace, "issue.created", "payload");

        assertThat(created.get(0).sent).isEqualTo(1);
        assertThat(created.get(1).sent).isZero();
    }

    @Test
    void publishToUserReachesOnlyThatUsersConnectionsInThatWorkspace() {
        UUID mia = UUID.randomUUID();
        UUID max = UUID.randomUUID();
        hub.register(workspace, mia);
        hub.register(workspace, mia);
        hub.register(workspace, max);
        hub.register(workspace);
        hub.register(UUID.randomUUID(), mia);

        hub.publishToUser(workspace, mia, "inbox.updated", "{}");

        assertThat(created).extracting(emitter -> emitter.sent).containsExactly(1, 1, 0, 0, 0);
    }

    @Test
    void aWorkspaceEventStillReachesConnectionsOfUsers() {
        hub.register(workspace, UUID.randomUUID());

        hub.publish(workspace, "issue.created", "payload");

        assertThat(created.getFirst().sent).isEqualTo(1);
    }

    @Test
    void publishToWorkspaceWithoutSubscribersDoesNothing() {
        hub.publish(workspace, "issue.created", "payload");

        assertThat(hub.subscriberCount(workspace)).isZero();
    }

    @Test
    void emitterIsRemovedWhenItCompletes() {
        SseEmitter emitter = hub.register(workspace);
        assertThat(hub.subscriberCount(workspace)).isEqualTo(1);

        created.get(0).fireCompletion();

        assertThat(emitter).isNotNull();
        assertThat(hub.subscriberCount(workspace)).isZero();
    }

    @Test
    void emitterIsRemovedWhenItTimesOut() {
        hub.register(workspace);

        created.get(0).fireTimeout();

        assertThat(hub.subscriberCount(workspace)).isZero();
    }

    @Test
    void failingEmitterIsRemovedAndHealthyOneKeepsReceiving() {
        hub.register(workspace);
        hub.register(workspace);
        created.get(0).failOnSend = true;

        hub.publish(workspace, "issue.updated", "payload");

        assertThat(hub.subscriberCount(workspace)).isEqualTo(1);
        assertThat(created.get(1).sent).isEqualTo(1);
    }

    @Test
    void heartbeatIsSentToEveryEmitterAndDropsBrokenOnes() {
        hub.register(workspace);
        hub.register(workspace);
        created.get(1).failOnSend = true;

        hub.sendHeartbeats();

        assertThat(created.get(0).sent).isEqualTo(1);
        assertThat(hub.subscriberCount(workspace)).isEqualTo(1);
    }

    @Test
    void theHeartbeatIsARealNamedEventSoThatBrowsersPassItToJavaScript() {
        hub.register(workspace);

        hub.sendHeartbeats();

        assertThat(created.get(0).text.toString()).contains("event:heartbeat");
    }

    /** Counts sends instead of writing to a real HTTP response. */
    private static class RecordingEmitter extends SseEmitter {

        int sent;
        final StringBuilder text = new StringBuilder();
        boolean failOnSend;
        private Runnable completion;
        private Runnable timeout;

        RecordingEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("client disconnected");
            }
            sent++;
            builder.build().forEach(part -> text.append(part.getData()));
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completion = callback;
            super.onCompletion(callback);
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeout = callback;
            super.onTimeout(callback);
        }

        void fireCompletion() {
            completion.run();
        }

        void fireTimeout() {
            timeout.run();
        }
    }
}
