package com.orchestragent.identity.infrastructure.adapter.out.storage;

import com.orchestragent.identity.application.port.AvatarStorage;
import com.orchestragent.identity.domain.Avatar;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.platform.storage.ObjectStorage;
import com.orchestragent.platform.storage.ObjectStorage.StoredObject;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Avatars as objects {@code identity/avatars/{userId}/{avatarId}} in the shared object storage. */
@Component
class ObjectStorageAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageAvatarStorage.class);

    private final ObjectStorage storage;

    ObjectStorageAvatarStorage(ObjectStorage storage) {
        this.storage = storage;
    }

    static String key(UserId userId, Avatar avatar) {
        return "identity/avatars/" + userId.value() + "/" + avatar.id();
    }

    @Override
    public void store(UserId userId, Avatar avatar, byte[] content) {
        storage.put(key(userId, avatar), content, avatar.type().mediaType());
    }

    @Override
    public Optional<byte[]> load(UserId userId, Avatar avatar) {
        return storage.get(key(userId, avatar)).map(StoredObject::content);
    }

    @Override
    public void discard(UserId userId, Avatar avatar) {
        try {
            storage.delete(key(userId, avatar));
        } catch (RuntimeException e) {
            log.warn("Could not delete the unused avatar object {}: {}", key(userId, avatar), e.toString());
        }
    }
}
