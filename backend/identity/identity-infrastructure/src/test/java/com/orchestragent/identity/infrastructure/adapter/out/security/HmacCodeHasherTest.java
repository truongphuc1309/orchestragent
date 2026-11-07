package com.orchestragent.identity.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.identity.domain.Email;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacCodeHasherTest {

    private static HmacCodeHasher hasherWithSecret(String secret) {
        return new HmacCodeHasher(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    }

    private final HmacCodeHasher hasher = hasherWithSecret("a-secret-that-is-at-least-32-bytes-long!");
    private final Email ada = Email.of("ada@example.com");

    @Test
    void matchesTheSameCodeForTheSameEmail() {
        String hash = hasher.hash(ada, "123456");

        assertThat(hasher.matches(ada, "123456", hash)).isTrue();
    }

    @Test
    void rejectsAWrongCode() {
        String hash = hasher.hash(ada, "123456");

        assertThat(hasher.matches(ada, "123457", hash)).isFalse();
    }

    @Test
    void sameCodeForADifferentEmailHashesDifferently() {
        assertThat(hasher.hash(ada, "123456")).isNotEqualTo(hasher.hash(Email.of("grace@example.com"), "123456"));
    }

    @Test
    void hashDependsOnTheServerSecret() {
        HmacCodeHasher other = hasherWithSecret("another-secret-that-is-at-least-32-bytes!");

        assertThat(other.hash(ada, "123456")).isNotEqualTo(hasher.hash(ada, "123456"));
    }

    @Test
    void hashDoesNotContainTheCode() {
        assertThat(hasher.hash(ada, "123456")).hasSize(64).doesNotContain("123456");
    }
}
