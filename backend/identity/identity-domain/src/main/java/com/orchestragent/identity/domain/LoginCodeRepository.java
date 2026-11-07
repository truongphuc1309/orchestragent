package com.orchestragent.identity.domain;

import java.time.Instant;
import java.util.Optional;

public interface LoginCodeRepository {

    void save(LoginCode code);

    /** When the most recent code for the email was issued; used for the resend rate limit. */
    Optional<Instant> findLatestCreatedAt(Email email);

    /**
     * The most recent code for the email, locked for update so that parallel guesses cannot exceed the
     * attempt limit.
     */
    Optional<LoginCode> findLatestForUpdate(Email email);
}
