package com.orchestragent.identity.application;

import java.util.UUID;

/** Everything about the signed-in user that the user may see and edit. */
public record ProfileView(UUID id, String email, String name, String bio, String avatarUrl) {
}
