package com.orchestragent.identity.contract;

import java.util.UUID;

/** Result of a successful API token check. */
public record VerifiedApiToken(UUID tokenId, UUID userId) {
}
