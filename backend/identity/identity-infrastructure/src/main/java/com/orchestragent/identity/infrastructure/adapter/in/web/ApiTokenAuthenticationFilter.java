package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.contract.ApiTokenVerifier;
import com.orchestragent.identity.contract.VerifiedApiToken;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates daemon requests with {@code Authorization: Bearer oat_...}. A request without the header is
 * passed on unauthenticated (and rejected by the authorization rules); a request with a bad or revoked token
 * is rejected right here. The header value is never logged.
 */
class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenVerifier verifier;

    ApiTokenAuthenticationFilter(ApiTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            reject(response);
            return;
        }
        Optional<VerifiedApiToken> verified = verifier.verify(header.substring(BEARER_PREFIX.length()).trim());
        if (verified.isEmpty()) {
            reject(response);
            return;
        }
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new CurrentUser(verified.get().userId(), null), null, List.of(new SimpleGrantedAuthority("ROLE_DAEMON")));
        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer");
    }
}
