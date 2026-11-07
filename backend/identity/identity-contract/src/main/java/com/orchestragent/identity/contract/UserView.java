package com.orchestragent.identity.contract;

import java.util.UUID;

/** Read-only view of a user that other contexts may use. */
public record UserView(UUID id, String email, String name) {
}
