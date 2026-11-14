package com.orchestragent.identity.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    List<User> findAllById(Collection<UserId> ids);

    /**
     * Stores the user unless the email is already taken, and returns whichever user owns the email. Safe when
     * two logins for a new address happen at the same time.
     */
    User createIfAbsent(User user);

    /** Sets the name and bio and leaves the avatar alone. False when there is no such user. */
    boolean updateProfile(UserId id, DisplayName name, Bio bio);

    /**
     * Sets the avatar ({@code null} removes it) and leaves name and bio alone, in one statement that also returns
     * the avatar it replaced. Two uploads at the same time therefore each learn the right picture to delete.
     * Empty when the user had no avatar (or does not exist).
     */
    Optional<Avatar> replaceAvatar(UserId id, Avatar avatar);
}
