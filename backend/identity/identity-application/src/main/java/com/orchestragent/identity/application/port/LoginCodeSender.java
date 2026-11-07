package com.orchestragent.identity.application.port;

import com.orchestragent.identity.domain.Email;
import java.time.Instant;

/** Delivers a login code to its owner. Implementations must never log the code. */
public interface LoginCodeSender {

    void send(Email to, String code, Instant expiresAt);
}
