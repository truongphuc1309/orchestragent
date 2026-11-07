package com.orchestragent.platform.config;

import com.orchestragent.platform.security.SecurityProperties;
import com.orchestragent.platform.sse.SseHub;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableAsync} makes {@code @ApplicationModuleListener} methods run on their own thread, after the commit. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(SecurityProperties.class)
public class PlatformConfig {

    @Bean
    SseHub sseHub() {
        return new SseHub();
    }

    /** Single time source for the whole application, so tests can replace it with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
