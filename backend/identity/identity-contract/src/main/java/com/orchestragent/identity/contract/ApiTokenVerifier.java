package com.orchestragent.identity.contract;

import java.util.Optional;

/** Checks a raw {@code oat_...} API token. Used by the daemon API filter. */
public interface ApiTokenVerifier {

    /**
     * @return the token owner when the token exists and is not revoked; empty otherwise. Also records the
     *         time of use.
     */
    Optional<VerifiedApiToken> verify(String rawToken);
}
