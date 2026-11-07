package com.orchestragent.inbox.application;

import com.orchestragent.inbox.contract.InboxChanged;
import com.orchestragent.shared.id.UuidV7;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link InboxChanged} inside the caller's transaction, so the live update is sent only after the
 * change is committed (by a listener of the event registry).
 */
@Component
class InboxEvents {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    InboxEvents(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    void changed(UUID workspaceId, UUID recipientId) {
        publisher.publishEvent(new InboxChanged(UuidV7.next(), clock.instant(), workspaceId, recipientId));
    }
}
