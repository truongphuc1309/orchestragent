package com.orchestragent.identity.domain;

import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

/**
 * The picture of a user. The file itself lives in object storage; the user keeps its ID and format. A new upload
 * gets a new ID, so an avatar's address never shows another picture and may be cached for good.
 */
public record Avatar(UUID id, ImageType type) {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public Avatar {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    /** A new avatar for the given file, after checking its size and format. */
    public static Avatar forUpload(byte[] content) {
        if (content != null && content.length > MAX_BYTES) {
            throw new ValidationException("avatar.too_large", "The picture must be at most 2 MB");
        }
        return new Avatar(UuidV7.next(), ImageType.detect(content));
    }
}
