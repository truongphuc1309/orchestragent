package com.orchestragent.identity.application.port;

import com.orchestragent.identity.domain.Avatar;
import com.orchestragent.identity.domain.UserId;
import java.util.Optional;

/** Where avatar pictures are kept (object storage). The user row only keeps the avatar's ID and format. */
public interface AvatarStorage {

    /** Stores the picture. Throws when the storage cannot be reached; nothing is stored then. */
    void store(UserId userId, Avatar avatar, byte[] content);

    /** The bytes of the picture; empty when they are not (or no longer) stored. */
    Optional<byte[]> load(UserId userId, Avatar avatar);

    /**
     * Deletes the picture if it is there. Best effort: never throws, because the picture is already unused when
     * this is called; a failure is logged and leaves an orphaned object behind.
     */
    void discard(UserId userId, Avatar avatar);
}
