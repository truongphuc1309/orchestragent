package com.orchestragent.identity.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import com.orchestragent.shared.error.ValidationException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> findById(UUID userId) {
        return users.findById(new UserId(userId)).map(UserDirectoryService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> findByEmail(String email) {
        try {
            return users.findByEmail(Email.of(email)).map(UserDirectoryService::toView);
        } catch (ValidationException invalidAddress) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserView> findByIds(Collection<UUID> userIds) {
        return users.findAllById(userIds.stream().map(UserId::new).toList()).stream()
                .map(UserDirectoryService::toView)
                .collect(Collectors.toMap(UserView::id, Function.identity()));
    }

    private static UserView toView(User user) {
        return new UserView(user.id().value(), user.email().value(), user.name());
    }
}
