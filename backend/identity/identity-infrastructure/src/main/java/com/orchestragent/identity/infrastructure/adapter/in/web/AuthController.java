package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.RequestLoginCode;
import com.orchestragent.identity.infrastructure.IdentityConfig;
import com.orchestragent.identity.application.VerifyLoginCode;
import com.orchestragent.identity.application.VerifyLoginCode.LoginResult;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.RequestCodeRequest;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.UserResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.VerifyCodeRequest;
import com.orchestragent.platform.ratelimit.SlidingWindowRateLimiter;
import com.orchestragent.platform.security.SecurityProperties;
import com.orchestragent.platform.security.SessionCookie;
import com.orchestragent.shared.error.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passwordless login: email in, code by email, code in, session cookie out. The JWT is only ever delivered
 * in the HttpOnly cookie, never in the response body.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RequestLoginCode requestLoginCode;
    private final VerifyLoginCode verifyLoginCode;
    private final SecurityProperties securityProperties;
    private final SlidingWindowRateLimiter authCodeRateLimiter;
    private final Clock clock;

    AuthController(
            RequestLoginCode requestLoginCode,
            VerifyLoginCode verifyLoginCode,
            SecurityProperties securityProperties,
            @Qualifier(IdentityConfig.AUTH_CODE_RATE_LIMITER) SlidingWindowRateLimiter authCodeRateLimiter,
            Clock clock) {
        this.requestLoginCode = requestLoginCode;
        this.verifyLoginCode = verifyLoginCode;
        this.securityProperties = securityProperties;
        this.authCodeRateLimiter = authCodeRateLimiter;
        this.clock = clock;
    }

    /**
     * Besides the limit of one code per address per minute (in {@link RequestLoginCode}), each client address may
     * ask for only a few codes per window, so one machine cannot send mail to many addresses or probe many
     * accounts. The address is the one the servlet container reports: behind a reverse proxy, turn on
     * {@code server.forward-headers-strategy=native}, otherwise every user looks like the proxy.
     */
    @PostMapping("/code")
    ResponseEntity<Void> requestCode(@Valid @RequestBody RequestCodeRequest request, HttpServletRequest http) {
        authCodeRateLimiter.tryAcquire(http.getRemoteAddr()).ifPresent(wait -> {
            throw new RateLimitedException(
                    "login.ip_rate_limited", "Too many login codes were requested from this address. Try again later.", wait);
        });
        requestLoginCode.execute(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    ResponseEntity<UserResponse> verify(@Valid @RequestBody VerifyCodeRequest request) {
        LoginResult result = verifyLoginCode.execute(request.email(), request.code());
        Duration maxAge = Duration.between(clock.instant(), result.expiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.accessToken(), maxAge).toString())
                .body(UserResponse.from(result.user()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(SessionCookie.NAME, value)
                .httpOnly(true)
                .secure(securityProperties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
