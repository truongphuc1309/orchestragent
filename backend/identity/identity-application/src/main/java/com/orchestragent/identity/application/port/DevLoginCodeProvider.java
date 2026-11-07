package com.orchestragent.identity.application.port;

import java.util.Optional;

/**
 * A fixed login code that works for any email. The only implementation that returns a value is active in the
 * {@code dev} profile; every other profile returns empty.
 */
public interface DevLoginCodeProvider {

    Optional<String> code();
}
