package com.orchestragent.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param jwtSecret    HS256 signing secret, read from the {@code JWT_SECRET} environment variable. Must be at
 *                     least 32 bytes.
 * @param cookieSecure whether the session and CSRF cookies get the {@code Secure} flag. Only the dev and test
 *                     profiles turn this off.
 */
@ConfigurationProperties("orchestragent.security")
public record SecurityProperties(String jwtSecret, @DefaultValue("true") boolean cookieSecure) {

    public static final int MIN_SECRET_BYTES = 32;
}
