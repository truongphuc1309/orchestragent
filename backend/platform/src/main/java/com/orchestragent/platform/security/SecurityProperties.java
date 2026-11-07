package com.orchestragent.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret HS256 signing secret, read from the {@code JWT_SECRET} environment variable. Must be at
 *                  least 32 bytes.
 */
@ConfigurationProperties("orchestragent.security")
public record SecurityProperties(String jwtSecret) {

    public static final int MIN_SECRET_BYTES = 32;
}
