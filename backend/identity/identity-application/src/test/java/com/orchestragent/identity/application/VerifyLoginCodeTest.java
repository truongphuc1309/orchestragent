package com.orchestragent.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.application.Fakes.FakeDevCode;
import com.orchestragent.identity.application.Fakes.FakeHasher;
import com.orchestragent.identity.application.Fakes.FakeIssuer;
import com.orchestragent.identity.application.Fakes.InMemoryLoginCodes;
import com.orchestragent.identity.application.Fakes.InMemoryUsers;
import com.orchestragent.identity.application.Fakes.MutableClock;
import com.orchestragent.identity.application.VerifyLoginCode.LoginResult;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.shared.error.UnauthorizedException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifyLoginCodeTest {

    private final InMemoryLoginCodes codes = new InMemoryLoginCodes();
    private final InMemoryUsers users = new InMemoryUsers();
    private final FakeDevCode devCode = new FakeDevCode();
    private final MutableClock clock = new MutableClock();
    private final FakeHasher hasher = new FakeHasher();
    private final VerifyLoginCode useCase =
            new VerifyLoginCode(codes, users, hasher, new FakeIssuer(), devCode, clock);
    private final Email ada = Email.of("ada@example.com");

    @BeforeEach
    void issueCodeForAda() {
        codes.save(LoginCode.issue(ada, hasher.hash(ada, "123456"), clock.instant()));
    }

    @Test
    void rightCodeSignsInAndCreatesTheUser() {
        LoginResult result = useCase.execute("ada@example.com", "123456");

        assertThat(result.accessToken()).startsWith("jwt-for-");
        assertThat(result.user().email()).isEqualTo("ada@example.com");
        assertThat(result.user().name()).isEqualTo("ada");
        assertThat(users.byEmail).containsKey(ada);
    }

    @Test
    void secondLoginReusesTheExistingUser() {
        LoginResult first = useCase.execute("ada@example.com", "123456");
        clock.advance(Duration.ofMinutes(2));
        codes.save(LoginCode.issue(ada, hasher.hash(ada, "654321"), clock.instant()));

        LoginResult second = useCase.execute("ADA@example.com", "654321");

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(users.byEmail).hasSize(1);
    }

    @Test
    void codeWithSurroundingWhitespaceIsAccepted() {
        assertThat(useCase.execute("ada@example.com", " 123456 ").user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void wrongCodeIsRejectedAndCounted() {
        assertThatThrownBy(() -> useCase.execute("ada@example.com", "000000")).isInstanceOf(UnauthorizedException.class);

        assertThat(codes.stored.get(0).attempts()).isEqualTo(1);
        assertThat(users.byEmail).isEmpty();
    }

    @Test
    void codeIsLockedAfterFiveWrongGuessesEvenIfTheNextOneIsRight() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> useCase.execute("ada@example.com", "000000"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> useCase.execute("ada@example.com", "123456")).isInstanceOf(UnauthorizedException.class);
        assertThat(codes.stored.get(0).attempts()).isEqualTo(5);
        assertThat(users.byEmail).isEmpty();
    }

    @Test
    void expiredCodeIsRejected() {
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> useCase.execute("ada@example.com", "123456")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void codeCanOnlyBeUsedOnce() {
        useCase.execute("ada@example.com", "123456");

        assertThatThrownBy(() -> useCase.execute("ada@example.com", "123456")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void newerCodeReplacesTheOlderOne() {
        clock.advance(Duration.ofMinutes(1));
        codes.save(LoginCode.issue(ada, hasher.hash(ada, "999999"), clock.instant()));

        assertThatThrownBy(() -> useCase.execute("ada@example.com", "123456")).isInstanceOf(UnauthorizedException.class);
        assertThat(useCase.execute("ada@example.com", "999999").user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void unknownAddressAndWrongCodeLookTheSame() {
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> useCase.execute("nobody@example.com", "123456"));
        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(
                () -> useCase.execute("ada@example.com", "000000"));

        assertThat(unknown).isInstanceOf(UnauthorizedException.class);
        assertThat(wrong).isInstanceOf(UnauthorizedException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
        assertThat(((UnauthorizedException) unknown).code()).isEqualTo(((UnauthorizedException) wrong).code());
    }

    @Test
    void devCodeIsAcceptedForAnyEmailWhenConfigured() {
        devCode.code = Optional.of("424242");

        LoginResult result = useCase.execute("someone-new@example.com", "424242");

        assertThat(result.user().email()).isEqualTo("someone-new@example.com");
    }

    @Test
    void devCodeIsRejectedWhenNotConfigured() {
        devCode.code = Optional.empty();

        assertThatThrownBy(() -> useCase.execute("someone-new@example.com", "424242"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(users.byEmail).doesNotContainKey(Email.of("someone-new@example.com"));
    }

    @Test
    void blankDevCodeNeverMatches() {
        devCode.code = Optional.of("");

        assertThatThrownBy(() -> useCase.execute("someone-new@example.com", "")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void nullCodeIsRejected() {
        assertThatThrownBy(() -> useCase.execute("ada@example.com", null)).isInstanceOf(UnauthorizedException.class);
    }
}
