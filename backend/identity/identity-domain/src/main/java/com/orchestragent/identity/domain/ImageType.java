package com.orchestragent.identity.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Arrays;
import java.util.Optional;

/**
 * The picture formats accepted for avatars. The format is read from the first bytes of the file, never from the
 * name or the {@code Content-Type} the client sends. SVG is refused on purpose: it can carry scripts.
 */
public enum ImageType {
    PNG("image/png"),
    JPEG("image/jpeg"),
    WEBP("image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_TAG = {'W', 'E', 'B', 'P'};

    private final String mediaType;

    ImageType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    /** The format of the file, or a validation error when it is empty or not a PNG, JPEG or WebP picture. */
    public static ImageType detect(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("avatar.empty", "The file is empty");
        }
        return sniff(content).orElseThrow(() -> new ValidationException(
                "avatar.unsupported_type", "The picture must be a PNG, JPEG or WebP image"));
    }

    private static Optional<ImageType> sniff(byte[] content) {
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
