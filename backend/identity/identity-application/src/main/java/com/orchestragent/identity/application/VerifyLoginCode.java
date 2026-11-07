package com.orchestragent.identity.application;

import com.orchestragent.identity.application.port.AccessTokenIssuer;
import com.orchestragent.identity.application.port.AccessTokenIssuer.IssuedAccessToken;
import com.orchestragent.identity.application.port.CodeHasher;
import com.orchestragent.identity.application.port.DevLoginCodeProvider;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.identity.domain.LoginCodeRepository;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserRepository;
import com.orchestragent.shared.error.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a login code and, when it is right, signs the user in (creating the user on first login).
 *
 * <p>Every failure looks the same to the caller so that nothing is revealed about which addresses have a
 * pending code. Failed attempts are stored even though the call ends with an exception, which is why this
 * transaction does not roll back on {@link UnauthorizedException}.
 */
@Service
public class VerifyLoginCode {

    static final String INVALID_CODE_MESSAGE = "The code is invalid or has expired";

    private final LoginCodeRepository codes;
    private final UserRepository users;
    private final CodeHasher hasher;
    private final AccessTokenIssuer tokenIssuer;
    private final DevLoginCodeProvider devCode;
    private final Clock clock;

    public VerifyLoginCode(
            LoginCodeRepository codes,
            UserRepository users,
            CodeHasher hasher,
            AccessTokenIssuer tokenIssuer,
            DevLoginCodeProvider devCode,
            Clock clock) {
        this.codes = codes;
        this.users = users;
        this.hasher = hasher;
        this.tokenIssuer = tokenIssuer;
        this.devCode = devCode;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResult execute(String rawEmail, String submittedCode) {
        Email email = Email.of(rawEmail);
        String code = submittedCode == null ? "" : submittedCode.trim();
        Instant now = clock.instant();

        if (!isDevCode(code)) {
            consumeEmailedCode(email, code, now);
        }

        User user = users.createIfAbsent(User.register(email, now));
        IssuedAccessToken token = tokenIssuer.issue(user);
        return new LoginResult(
                token.value(), token.expiresAt(), new UserView(user.id().value(), user.email().value(), user.name()));
    }

    private void consumeEmailedCode(Email email, String submittedCode, Instant now) {
        LoginCode stored = codes.findLatestForUpdate(email)
                .filter(candidate -> candidate.canBeTried(now))
                .orElseThrow(() -> new UnauthorizedException("login.invalid_code", INVALID_CODE_MESSAGE));

        if (!hasher.matches(email, submittedCode, stored.codeHash())) {
            stored.recordFailedAttempt();
            codes.save(stored);
            throw new UnauthorizedException("login.invalid_code", INVALID_CODE_MESSAGE);
        }
        stored.markUsed(now);
        codes.save(stored);
    }

    private boolean isDevCode(String submittedCode) {
        return devCode.code()
                .filter(expected -> !expected.isBlank())
                .map(expected -> MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), submittedCode.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    public record LoginResult(String accessToken, Instant expiresAt, UserView user) {
    }
}
