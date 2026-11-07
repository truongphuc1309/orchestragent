package com.orchestragent.inbox.infrastructure.adapter.out.sse;

import com.orchestragent.inbox.contract.InboxChanged;
import com.orchestragent.platform.sse.SseHub;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Tells only the person whose inbox changed ({@code inbox.updated}, no data): their browsers fetch the inbox and
 * the unread count again. Nobody else learns that someone got a notification.
 */
@Component
class InboxSseListener {

    static final String INBOX_UPDATED = "inbox.updated";

    private final SseHub hub;

    InboxSseListener(SseHub hub) {
        this.hub = hub;
    }

    @ApplicationModuleListener
    void on(InboxChanged event) {
        hub.publishToUser(event.workspaceId(), event.recipientId(), INBOX_UPDATED, Map.of());
    }
}
