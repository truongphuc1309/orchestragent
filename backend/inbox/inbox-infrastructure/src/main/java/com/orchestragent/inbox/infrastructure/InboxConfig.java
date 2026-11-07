package com.orchestragent.inbox.infrastructure;

import com.orchestragent.platform.persistence.ModuleMigration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InboxConfig {

    /** Runs after identity (10), workspace (20), issue (30) and agent (40). */
    @Bean
    ModuleMigration inboxMigration() {
        return new ModuleMigration("inbox", "classpath:db/migration/inbox", 50);
    }
}
