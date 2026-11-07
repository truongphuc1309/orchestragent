package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.RequestLoginCode;
import com.orchestragent.identity.application.VerifyLoginCode;
import com.orchestragent.identity.application.VerifyLoginCode.LoginResult;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.RequestCodeRequest;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.UserResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.VerifyCodeRequest;
import com.orchestragent.platform.security.SecurityProperties;
import com.orchestragent.platform.security.SessionCookie;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
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
    private final Clock clock;

    AuthController(
            RequestLoginCode requestLoginCode,
            VerifyLoginCode verifyLoginCode,
            SecurityProperties securityProperties,
            Clock clock) {
        this.requestLoginCode = requestLoginCode;
        this.verifyLoginCode = verifyLoginCode;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @PostMapping("/code")
    ResponseEntity<Void> requestCode(@Valid @RequestBody RequestCodeRequest request) {
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
