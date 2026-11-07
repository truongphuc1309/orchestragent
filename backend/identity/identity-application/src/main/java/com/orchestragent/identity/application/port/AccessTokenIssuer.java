package com.orchestragent.identity.application.port;

import com.orchestragent.identity.domain.User;
import java.time.Instant;

/** Issues the signed token a browser session uses after logging in. */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
