package com.orchestragent.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where uploaded files are kept: an S3-compatible object store (RustFS in development).
 *
 * @param endpoint  URL of the store, for example {@code http://localhost:9000}
 * @param accessKey access key; empty means anonymous requests
 * @param secretKey secret key
 * @param bucket    the one bucket of the application; created on first write when it is missing. Every context
 *                  writes under its own key prefix ({@code identity/...}).
 */
@ConfigurationProperties("orchestragent.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey,
        @DefaultValue("orchestragent") String bucket) {
}
