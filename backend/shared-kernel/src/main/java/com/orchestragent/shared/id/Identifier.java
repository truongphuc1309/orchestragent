package com.orchestragent.shared.id;

import java.util.UUID;

/**
 * Marker for strongly typed identifiers. Each aggregate defines its own record, for example
 * {@code record IssueId(UUID value) implements Identifier}, so IDs of different types cannot be mixed up.
 */
public interface Identifier {

    UUID value();
}
