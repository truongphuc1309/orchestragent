package com.orchestragent.platform.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security for browser clients. A request is authenticated either by a JWT in the {@code Authorization}
 * header (OAuth2 resource server) or by the same JWT in the HttpOnly {@code oa_session} cookie
 * ({@link SessionCookieAuthenticationFilter}). Cookie-authenticated writes are protected by CSRF tokens; the
 * resource server automatically exempts only requests with an {@code Authorization} header. The daemon API
 * has its own filter chain (API tokens) in the identity context.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Last in line: contexts register more specific chains (for example {@code /api/daemon/**}) before it. */
    @Bean
    @Order(100)
    SecurityFilterChain browserFilterChain(HttpSecurity http, SecurityProperties properties, JwtDecoder jwtDecoder)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(properties.cookieSecure()));
        return http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // The cookie value is sent back as-is in a header; BREACH masking is not needed for JSON.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Stateless: Spring treats every authenticated request as a new login and would rotate
                        // the CSRF token each time, so a client's next write carries a stale token. There is no
                        // session to protect here, so the token is simply kept.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new SessionCookieAuthenticationFilter(jwtDecoder), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Error pages must stay reachable, otherwise a 403 (for example a failed CSRF check) is
                        // forwarded to /error and shows up as a misleading 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/code", "/api/auth/verify", "/api/auth/logout")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(SessionCookie.JWT_ISSUER));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.getBytes(UTF_8).length < SecurityProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + SecurityProperties.MIN_SECRET_BYTES + " bytes long");
        }
        return new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
    }
}
