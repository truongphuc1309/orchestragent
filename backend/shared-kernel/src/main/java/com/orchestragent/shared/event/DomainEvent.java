package com.orchestragent.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in a bounded context. Integration events published across contexts implement
 * this interface and live in the publishing context's {@code *-contract} module.
 */
public interface DomainEvent {

    /** Unique ID of this event occurrence. Listeners use it to stay idempotent. */
    UUID eventId();

    Instant occurredAt();
}
