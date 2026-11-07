package com.orchestragent.issue.infrastructure;

import com.orchestragent.platform.persistence.ModuleMigration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class IssueConfig {

    @Bean
    ModuleMigration issueMigration() {
        return new ModuleMigration("issue", "classpath:db/migration/issue", 30);
    }
}
