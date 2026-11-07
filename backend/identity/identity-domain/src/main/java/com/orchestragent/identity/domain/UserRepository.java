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
}
