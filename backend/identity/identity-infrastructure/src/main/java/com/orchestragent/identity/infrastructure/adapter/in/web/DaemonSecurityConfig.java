package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.contract.ApiTokenVerifier;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Security chain of the daemon API: API tokens only, never cookies, no sessions, no CSRF (there is no browser
 * session to forge). Runs before the browser chain of the platform.
 */
@Configuration(proxyBeanMethods = false)
class DaemonSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain daemonFilterChain(HttpSecurity http, ApiTokenVerifier verifier) throws Exception {
        return http
                .securityMatcher("/api/daemon/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasRole("DAEMON"))
                .addFilterBefore(new ApiTokenAuthenticationFilter(verifier), BasicAuthenticationFilter.class)
                .build();
    }
}
