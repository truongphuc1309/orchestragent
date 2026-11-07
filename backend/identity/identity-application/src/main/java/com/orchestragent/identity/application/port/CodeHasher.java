package com.orchestragent.identity.application.port;

import com.orchestragent.identity.domain.Email;

/**
 * Hashes login codes for storage. A 6-digit code has only a million possibilities, so the hash must be keyed
 * with a server secret; a plain hash could be reversed instantly if the table leaked.
 */
public interface CodeHasher {

    String hash(Email email, String code);

    /** Constant-time comparison of a submitted code with a stored hash. */
    boolean matches(Email email, String code, String storedHash);
}
