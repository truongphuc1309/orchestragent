package com.orchestragent.identity.infrastructure.adapter.out.security;

import com.orchestragent.identity.application.port.AccessTokenIssuer;
import com.orchestragent.identity.domain.User;
import com.orchestragent.platform.security.SessionCookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Signs HS256 JWTs valid for 7 days with {@code sub} = user ID and an {@code email} claim. */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final Duration LIFETIME = Duration.ofDays(7);

    private final JwtEncoder encoder;
    private final Clock clock;

    JwtAccessTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SessionCookie.JWT_ISSUER)
                .subject(user.id().value().toString())
                .claim("email", user.email().value())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
