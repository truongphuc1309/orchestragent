package com.orchestragent.platform.security;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects {@link CurrentUser} into controller methods. Browser requests carry a JWT ({@code sub} = user ID);
 * daemon requests are authenticated by an API token filter that stores a {@link CurrentUser} as principal.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        if (authentication.getPrincipal() instanceof CurrentUser currentUser) {
            return currentUser;
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            return fromJwt(token);
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    private static CurrentUser fromJwt(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        try {
            return new CurrentUser(UUID.fromString(subject), token.getToken().getClaimAsString("email"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthenticationCredentialsNotFoundException("JWT subject is not a user ID");
        }
    }
}
