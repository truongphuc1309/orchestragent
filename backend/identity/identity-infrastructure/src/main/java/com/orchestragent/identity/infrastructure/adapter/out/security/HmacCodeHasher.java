package com.orchestragent.identity.infrastructure.adapter.out.security;

import com.orchestragent.identity.application.port.CodeHasher;
import com.orchestragent.identity.domain.Email;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 of {@code email + code} under a key derived from the server secret. The email is part of the
 * message so identical codes of different users never hash equally, and the derived key keeps this use of the
 * secret separate from JWT signing.
 */
@Component
class HmacCodeHasher implements CodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] KEY_LABEL = "orchestragent:login-code:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    HmacCodeHasher(SecretKey serverSecret) {
        this.key = new SecretKeySpec(hmac(serverSecret.getEncoded(), KEY_LABEL), ALGORITHM);
    }

    @Override
    public String hash(Email email, String code) {
        byte[] message = (email.value() + '\0' + code).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(hmac(key.getEncoded(), message));
    }

    @Override
    public boolean matches(Email email, String code, String storedHash) {
        return MessageDigest.isEqual(
                hash(email, code).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] keyBytes, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
