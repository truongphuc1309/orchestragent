package com.orchestragent.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Authenticates browser requests from the HttpOnly session cookie.
 *
 * <p>This is deliberately NOT a bearer token resolver of the OAuth2 resource server. Spring Security exempts
 * every request that carries a resolved bearer token from CSRF protection, which is right for an
 * {@code Authorization} header (a cross-site page cannot set one) but wrong for a cookie, which browsers
 * attach automatically. Keeping the cookie out of the resource server keeps CSRF protection on for it.
 *
 * <p>A missing, expired or invalid cookie simply leaves the request anonymous, so a stale cookie never
 * blocks public endpoints such as the login. Requests with an {@code Authorization} header are left to the
 * resource server.
 */
class SessionCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    SessionCookieAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader("Authorization") == null) {
            Cookie cookie = WebUtils.getCookie(request, SessionCookie.NAME);
            if (cookie != null && StringUtils.hasText(cookie.getValue())) {
                authenticate(cookie.getValue());
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(converter.convert(jwt));
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
        } catch (JwtException invalidOrExpired) {
            // Anonymous request: protected endpoints answer 401, public ones work as usual.
        }
    }
}
