package com.orchestragent.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginCodeTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final Email email = Email.of("ada@example.com");

    @Test
    void newCodeExpiresAfterTenMinutes() {
        LoginCode code = LoginCode.issue(email, "hash", NOW);

        assertThat(code.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(code.canBeTried(NOW.plusSeconds(599))).isTrue();
        assertThat(code.canBeTried(NOW.plusSeconds(600))).isFalse();
    }

    @Test
    void codeIsLockedAfterFiveFailedAttempts() {
        LoginCode code = LoginCode.issue(email, "hash", NOW);

        for (int i = 0; i < LoginCode.MAX_ATTEMPTS - 1; i++) {
            code.recordFailedAttempt();
            assertThat(code.canBeTried(NOW)).isTrue();
        }
        code.recordFailedAttempt();

        assertThat(code.attempts()).isEqualTo(5);
        assertThat(code.canBeTried(NOW)).isFalse();
    }

    @Test
    void usedCodeCannotBeTriedAgain() {
        LoginCode code = LoginCode.issue(email, "hash", NOW);

        code.markUsed(NOW.plusSeconds(5));

        assertThat(code.usedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(code.canBeTried(NOW.plusSeconds(6))).isFalse();
    }
}
