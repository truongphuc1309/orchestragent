package com.orchestragent.identity.contract;

import java.util.Optional;
import java.util.UUID;

/** Synchronous lookup of users for other contexts. Becomes an HTTP client if identity is split out. */
public interface UserDirectory {

    Optional<UserView> findById(UUID userId);

    /** The email is normalized (trimmed, lower-cased) before the lookup. */
    Optional<UserView> findByEmail(String email);
}
