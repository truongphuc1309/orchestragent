package com.orchestragent.identity.application;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import com.orchestragent.shared.error.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMe {

    private final UserRepository users;

    public GetMe(UserRepository users) {
        this.users = users;
    }

    /** A valid token for a user that no longer exists is treated as not authenticated. */
    @Transactional(readOnly = true)
    public UserView execute(UUID userId) {
        return users.findById(new UserId(userId))
                .map(user -> new UserView(user.id().value(), user.email().value(), user.name()))
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }
}
