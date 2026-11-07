package com.orchestragent.agent.infrastructure;

import com.orchestragent.platform.persistence.ModuleMigration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AgentConfig {

    /** Runs after identity (10), workspace (20) and issue (30). */
    @Bean
    ModuleMigration agentMigration() {
        return new ModuleMigration("agent", "classpath:db/migration/agent", 40);
    }
}
