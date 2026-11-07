package com.orchestragent.identity.infrastructure;

import com.orchestragent.identity.application.port.DevLoginCodeProvider;
import com.orchestragent.platform.persistence.ModuleMigration;
import com.orchestragent.platform.ratelimit.SlidingWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
public class IdentityConfig {

    public static final String AUTH_CODE_RATE_LIMITER = "authCodeRateLimiter";

    /** Identity runs first among the contexts: everything else refers to users. */
    @Bean
    ModuleMigration identityMigration() {
        return new ModuleMigration("identity", "classpath:db/migration/identity", 10);
    }

    /**
     * The fixed dev login code, active ONLY in the {@code dev} profile. The configured value is read here and
     * nowhere else, so no other profile can ever see it.
     */
    @Bean
    @Profile("dev")
    DevLoginCodeProvider devLoginCodeProvider(@Value("${orchestragent.dev-login-code:}") String code) {
        return () -> code.isBlank() ? Optional.empty() : Optional.of(code);
    }

    @Bean
    @Profile("!dev")
    DevLoginCodeProvider disabledDevLoginCodeProvider() {
        return Optional::empty;
    }

    /**
     * Login codes per client address: at most {@code max-requests} in each {@code window}. The defaults (10 in
     * 10 minutes) are generous for people behind a shared office address and tight for a script.
     */
    @Bean(AUTH_CODE_RATE_LIMITER)
    SlidingWindowRateLimiter authCodeRateLimiter(
            @Value("${orchestragent.rate-limit.auth-code.max-requests:10}") int maxRequests,
            @Value("${orchestragent.rate-limit.auth-code.window:10m}") Duration window,
            Clock clock) {
        return new SlidingWindowRateLimiter(maxRequests, window, clock);
    }
}
