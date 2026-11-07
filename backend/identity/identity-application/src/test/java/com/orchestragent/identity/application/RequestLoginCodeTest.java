package com.orchestragent.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.application.Fakes.CapturingSender;
import com.orchestragent.identity.application.Fakes.FakeHasher;
import com.orchestragent.identity.application.Fakes.FixedCodeGenerator;
import com.orchestragent.identity.application.Fakes.InMemoryLoginCodes;
import com.orchestragent.identity.application.Fakes.MutableClock;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.shared.error.RateLimitedException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestLoginCodeTest {

    private final InMemoryLoginCodes codes = new InMemoryLoginCodes();
    private final FixedCodeGenerator generator = new FixedCodeGenerator();
    private final CapturingSender sender = new CapturingSender();
    private final MutableClock clock = new MutableClock();
    private final RequestLoginCode useCase =
            new RequestLoginCode(codes, generator, new FakeHasher(), sender, clock);

    @Test
    void sendsTheCodeAndStoresOnlyItsHash() {
        useCase.execute("ada@example.com");

        assertThat(sender.codes).containsExactly("123456");
        assertThat(codes.stored).hasSize(1);
        LoginCode stored = codes.stored.get(0);
        assertThat(stored.codeHash()).isEqualTo(new FakeHasher().hash(stored.email(), "123456"));
        assertThat(stored.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    }

    @Test
    void normalizesTheEmailBeforeUsingIt() {
        useCase.execute("  ADA@Example.com ");

        assertThat(sender.recipients).extracting(Object::toString).containsExactly("ada@example.com");
        assertThat(codes.stored.get(0).email().value()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsASecondRequestWithinSixtySeconds() {
        useCase.execute("ada@example.com");
        clock.advance(Duration.ofSeconds(59));

        assertThatThrownBy(() -> useCase.execute("ADA@example.com"))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(1));
        assertThat(sender.codes).hasSize(1);
        assertThat(codes.stored).hasSize(1);
    }

    @Test
    void allowsAnotherCodeAfterSixtySeconds() {
        useCase.execute("ada@example.com");
        clock.advance(Duration.ofSeconds(60));

        useCase.execute("ada@example.com");

        assertThat(sender.codes).hasSize(2);
    }

    @Test
    void rateLimitIsPerEmailAddress() {
        useCase.execute("ada@example.com");

        useCase.execute("grace@example.com");

        assertThat(sender.codes).hasSize(2);
    }

    @Test
    void invalidEmailSendsNothing() {
        assertThatThrownBy(() -> useCase.execute("not-an-email")).isInstanceOf(ValidationException.class);

        assertThat(sender.codes).isEmpty();
        assertThat(codes.stored).isEmpty();
    }
}
