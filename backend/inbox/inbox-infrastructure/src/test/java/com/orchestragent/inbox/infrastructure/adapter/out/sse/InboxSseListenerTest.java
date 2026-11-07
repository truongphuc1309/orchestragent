package com.orchestragent.inbox.infrastructure.adapter.out.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.inbox.contract.InboxChanged;
import com.orchestragent.platform.sse.SseHub;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxSseListenerTest {

    /** Records what would be sent, and to whom. */
    static final class RecordingHub extends SseHub {
        final List<String> sent = new ArrayList<>();

        @Override
        public void publish(UUID workspaceId, String eventType, Object payload) {
            sent.add("everyone in " + workspaceId + ": " + eventType);
        }

        @Override
        public void publishToUser(UUID workspaceId, UUID userId, String eventType, Object payload) {
            sent.add(userId + " in " + workspaceId + ": " + eventType);
        }
    }

    @Test
    void tellsOnlyThePersonWhoseInboxChanged() {
        RecordingHub hub = new RecordingHub();
        UUID workspace = UUID.randomUUID();
        UUID mia = UUID.randomUUID();

        new InboxSseListener(hub).on(new InboxChanged(UUID.randomUUID(), Instant.now(), workspace, mia));

        assertThat(hub.sent).containsExactly(mia + " in " + workspace + ": inbox.updated");
    }
}
