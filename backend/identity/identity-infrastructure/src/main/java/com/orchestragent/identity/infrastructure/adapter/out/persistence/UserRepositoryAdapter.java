package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import com.orchestragent.identity.domain.Avatar;
import com.orchestragent.identity.domain.Bio;
import com.orchestragent.identity.domain.DisplayName;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.ImageType;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Users are read through JPA and changed with plain SQL. Each change touches only its own columns, so saving the
 * name never puts back an avatar that an upload replaced in the meantime.
 */
@Repository
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final JdbcClient jdbc;

    UserRepositoryAdapter(UserJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
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

    @Override
    public boolean updateProfile(UserId id, DisplayName name, Bio bio) {
        return jdbc.sql("UPDATE identity.users SET name = :name, bio = :bio WHERE id = :id")
                .param("id", id.value())
                .param("name", name.value())
                .param("bio", bio.value())
                .update() == 1;
    }

    /** The sub-select locks the row and keeps its old values, which {@code RETURNING} then reports. */
    @Override
    public Optional<Avatar> replaceAvatar(UserId id, Avatar avatar) {
        return jdbc.sql("""
                        UPDATE identity.users AS u
                        SET avatar_id = CAST(:avatarId AS uuid), avatar_type = CAST(:avatarType AS text)
                        FROM (SELECT id, avatar_id, avatar_type FROM identity.users WHERE id = :id FOR UPDATE) AS old
                        WHERE u.id = old.id
                        RETURNING old.avatar_id, old.avatar_type
                        """)
                .param("id", id.value())
                .param("avatarId", avatar == null ? null : avatar.id())
                .param("avatarType", avatar == null ? null : avatar.type().name())
                .query((rs, row) -> avatarOf(rs.getObject("avatar_id", UUID.class), rs.getString("avatar_type")))
                .list()
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.getId()),
                Email.of(entity.getEmail()),
                entity.getName(),
                entity.getBio(),
                avatarOf(entity.getAvatarId(), entity.getAvatarType()),
                entity.getCreatedAt());
    }

    private static Avatar avatarOf(UUID avatarId, String avatarType) {
        return avatarId == null ? null : new Avatar(avatarId, ImageType.valueOf(avatarType));
    }
}
