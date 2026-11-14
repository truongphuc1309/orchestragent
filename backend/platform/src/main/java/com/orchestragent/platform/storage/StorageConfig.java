package com.orchestragent.platform.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    ObjectStorage objectStorage(StorageProperties properties) {
        return S3ObjectStorage.from(properties);
    }
}
