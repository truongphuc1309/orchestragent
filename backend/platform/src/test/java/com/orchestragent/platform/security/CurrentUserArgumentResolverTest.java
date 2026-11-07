package com.orchestragent.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserFromJwtSubjectAndEmailClaim() {
        UUID userId = UUID.randomUUID();
        authenticate(jwt(userId.toString()));

        Object resolved = resolver.resolveArgument(null, null, null, null);

        assertThat(resolved).isEqualTo(new CurrentUser(userId, "ada@example.com"));
    }

    @Test
    void rejectsSubjectThatIsNotAUuid() {
        authenticate(jwt("not-a-uuid"));

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void resolvesPrincipalSetByApiTokenFilter() {
        CurrentUser principal = new CurrentUser(UUID.randomUUID(), null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(resolver.resolveArgument(null, null, null, null)).isEqualTo(principal);
    }

    @Test
    void rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pw"));

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static Jwt jwt(String subject) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", subject, "email", "ada@example.com"));
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
