package com.orchestragent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * An S3-compatible object store (RustFS, the same image as docker-compose.yml) for integration tests. Like
 * Postgres, the container is a bean, so it lives as long as the Spring context. The credentials are throwaway
 * test values.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestConfiguration {

    public static final String IMAGE = "rustfs/rustfs:1.0.5";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key-not-real";

    @Bean
    GenericContainer<?> objectStore() {
        return new GenericContainer<>(IMAGE)
                .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                .withEnv("RUSTFS_CONSOLE_ENABLE", "false")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/health").forPort(9000).forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar objectStoreProperties(GenericContainer<?> objectStore) {
        return registry -> {
            registry.add("orchestragent.storage.endpoint",
                    () -> "http://" + objectStore.getHost() + ":" + objectStore.getMappedPort(9000));
            registry.add("orchestragent.storage.access-key", () -> ACCESS_KEY);
            registry.add("orchestragent.storage.secret-key", () -> SECRET_KEY);
            registry.add("orchestragent.storage.bucket", () -> "orchestragent-test");
        };
    }
}
