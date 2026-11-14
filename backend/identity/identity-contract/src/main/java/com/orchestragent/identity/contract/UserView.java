package com.orchestragent.identity.contract;

import java.util.UUID;

/**
 * Read-only view of a user that other contexts may use.
 *
 * @param avatarUrl address of the user's picture, relative to the API origin
 *                  ({@code /api/users/{id}/avatar/{avatarId}}); null when the user has none. The address changes
 *                  with every new picture, so it may be cached.
 */
public record UserView(UUID id, String email, String name, String avatarUrl) {
}
