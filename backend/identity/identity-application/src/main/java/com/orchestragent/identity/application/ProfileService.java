package com.orchestragent.identity.application;

import com.orchestragent.identity.application.port.AvatarStorage;
import com.orchestragent.identity.domain.Avatar;
import com.orchestragent.identity.domain.Bio;
import com.orchestragent.identity.domain.DisplayName;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user edits their own profile: name, bio and avatar. Only the user themselves can change it, so
 * every method takes the ID of the caller and nothing else identifies the user.
 *
 * <p>Avatar changes are not one database transaction: the picture is stored first, then the user row points to
 * it, then the old picture is deleted. A failure in the middle leaves at worst an unused object, never a user
 * pointing to a missing picture. Storage calls never run inside a database transaction.
 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final AvatarStorage storage;

    public ProfileService(UserRepository users, AvatarStorage storage) {
        this.users = users;
        this.storage = storage;
    }

    @Transactional
    public ProfileView update(UUID userId, String name, String bio) {
        // Validate both before writing anything.
        DisplayName displayName = DisplayName.of(name);
        Bio about = Bio.of(bio);
        UserId id = new UserId(userId);
        if (!users.updateProfile(id, displayName, about)) {
            throw gone();
        }
        return Profiles.profileView(existing(id));
    }

    public ProfileView changeAvatar(UUID userId, byte[] content) {
        UserId id = new UserId(userId);
        existing(id);
        Avatar avatar = Avatar.forUpload(content);
        storage.store(id, avatar, content);
        Optional<Avatar> previous;
        try {
            previous = users.replaceAvatar(id, avatar);
        } catch (RuntimeException e) {
            storage.discard(id, avatar);
            throw e;
        }
        previous.ifPresent(old -> storage.discard(id, old));
        return Profiles.profileView(existing(id));
    }

    public ProfileView removeAvatar(UUID userId) {
        UserId id = new UserId(userId);
        existing(id);
        users.replaceAvatar(id, null).ifPresent(old -> storage.discard(id, old));
        return Profiles.profileView(existing(id));
    }

    /**
     * The picture behind an avatar address. Only the user's current avatar is served: an old address (the picture
     * was replaced or removed) is not found, like an unknown one.
     */
    public AvatarPicture avatar(UUID userId, UUID avatarId) {
        UserId id = new UserId(userId);
        Avatar avatar = users.findById(id)
                .flatMap(User::avatar)
                .filter(current -> current.id().equals(avatarId))
                .orElseThrow(ProfileService::avatarNotFound);
        byte[] content = storage.load(id, avatar).orElseThrow(ProfileService::avatarNotFound);
        return new AvatarPicture(content, avatar.type().mediaType());
    }

    private User existing(UserId id) {
        return users.findById(id).orElseThrow(ProfileService::gone);
    }

    /** Same as {@link GetMe}: a valid session for a user that no longer exists is not authenticated. */
    private static UnauthorizedException gone() {
        return new UnauthorizedException("User no longer exists");
    }

    private static NotFoundException avatarNotFound() {
        return new NotFoundException("avatar.not_found", "Avatar not found");
    }

    public record AvatarPicture(byte[] content, String mediaType) {
    }
}
