package com.orchestragent.platform.security;

import java.util.UUID;

/** The authenticated user of the current request, resolved from the JWT. Use it as a controller parameter. */
public record CurrentUser(UUID id, String email) {
}
