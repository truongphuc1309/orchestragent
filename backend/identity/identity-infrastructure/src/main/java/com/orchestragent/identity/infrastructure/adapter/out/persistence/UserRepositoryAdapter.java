package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public List<User> findAllById(Collection<UserId> ids) {
        return jpa.findAllById(ids.stream().map(UserId::value).toList()).stream()
                .map(UserRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public User createIfAbsent(User user) {
        jpa.insertIfAbsent(user.id().value(), user.email().value(), user.name(), user.createdAt());
        return jpa.findByEmail(user.email().value())
                .map(UserRepositoryAdapter::toDomain)
                .orElseThrow(() -> new IllegalStateException("User vanished right after insert"));
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.getId()), Email.of(entity.getEmail()), entity.getName(), entity.getCreatedAt());
    }
}
