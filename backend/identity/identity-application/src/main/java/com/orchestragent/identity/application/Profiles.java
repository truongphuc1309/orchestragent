package com.orchestragent.identity.application;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.identity.domain.User;

/** The one place that turns a {@link User} into the views other code sees. */
final class Profiles {

    private Profiles() {
    }

    /** The address served by the avatar endpoint, or null without a picture. */
    static String avatarUrl(User user) {
        return user.avatar()
                .map(avatar -> "/api/users/" + user.id().value() + "/avatar/" + avatar.id())
                .orElse(null);
    }

    static UserView userView(User user) {
        return new UserView(user.id().value(), user.email().value(), user.name(), avatarUrl(user));
    }

    static ProfileView profileView(User user) {
        return new ProfileView(user.id().value(), user.email().value(), user.name(), user.bio(), avatarUrl(user));
    }
}
