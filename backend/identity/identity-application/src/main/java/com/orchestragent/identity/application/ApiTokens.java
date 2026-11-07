package com.orchestragent.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generation and hashing rules of API tokens ({@code oat_<random>}). */
public final class ApiTokens {

    public static final String PREFIX = "oat_";

    /** The stored display prefix: {@code oat_} plus the first characters of the random part. */
    static final int DISPLAY_PREFIX_LENGTH = PREFIX.length() + 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiTokens() {
    }

    /** 256 bits of randomness, so a fast unsalted hash is enough to store it. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean looksLikeApiToken(String raw) {
        return raw != null && raw.startsWith(PREFIX) && raw.length() > PREFIX.length();
    }

    public static String displayPrefix(String raw) {
        return raw.substring(0, Math.min(raw.length(), DISPLAY_PREFIX_LENGTH));
    }

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
