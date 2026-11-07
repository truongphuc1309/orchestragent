package com.orchestragent.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final UserId owner = UserId.generate();

    private ApiToken token() {
        return ApiToken.create(owner, "laptop", "oat_abcd1234", "hash", NOW);
    }

    @Test
    void revokeKeepsTheFirstRevocationTime() {
        ApiToken token = token();

        token.revoke(NOW.plusSeconds(10));
        token.revoke(NOW.plusSeconds(99));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.revokedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void recordUseOnlyWritesOncePerMinute() {
        ApiToken token = token();

        assertThat(token.recordUse(NOW)).isTrue();
        assertThat(token.recordUse(NOW.plusSeconds(30))).isFalse();
        assertThat(token.lastUsedAt()).isEqualTo(NOW);
        assertThat(token.recordUse(NOW.plusSeconds(61))).isTrue();
        assertThat(token.lastUsedAt()).isEqualTo(NOW.plusSeconds(61));
    }

    @Test
    void ownership() {
        ApiToken token = token();

        assertThat(token.isOwnedBy(owner)).isTrue();
        assertThat(token.isOwnedBy(UserId.generate())).isFalse();
    }
}
