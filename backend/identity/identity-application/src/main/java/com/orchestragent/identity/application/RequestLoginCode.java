package com.orchestragent.identity.application;

import com.orchestragent.identity.application.port.CodeHasher;
import com.orchestragent.identity.application.port.LoginCodeGenerator;
import com.orchestragent.identity.application.port.LoginCodeSender;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.identity.domain.LoginCodeRepository;
import com.orchestragent.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a login code for an email address and sends it. At most one code per address per minute. */
@Service
public class RequestLoginCode {

    private final LoginCodeRepository codes;
    private final LoginCodeGenerator generator;
    private final CodeHasher hasher;
    private final LoginCodeSender sender;
    private final Clock clock;

    public RequestLoginCode(
            LoginCodeRepository codes,
            LoginCodeGenerator generator,
            CodeHasher hasher,
            LoginCodeSender sender,
            Clock clock) {
        this.codes = codes;
        this.generator = generator;
        this.hasher = hasher;
        this.sender = sender;
        this.clock = clock;
    }

    @Transactional
    public void execute(String rawEmail) {
        Email email = Email.of(rawEmail);
        Instant now = clock.instant();

        codes.findLatestCreatedAt(email)
                .filter(last -> last.plus(LoginCode.RESEND_INTERVAL).isAfter(now))
                .ifPresent(last -> {
                    long wait = Math.max(1, Duration.between(now, last.plus(LoginCode.RESEND_INTERVAL)).toSeconds());
                    throw new RateLimitedException(
                            "login.code_rate_limited", "A login code was sent recently. Try again later.", wait);
                });

        String code = generator.generate();
        LoginCode loginCode = LoginCode.issue(email, hasher.hash(email, code), now);
        codes.save(loginCode);
        sender.send(email, code, loginCode.expiresAt());
    }
}
