package com.orchestragent.workspace.infrastructure;

import com.orchestragent.platform.persistence.ModuleMigration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WorkspaceConfig {

    /** Runs after identity (order 10): workspaces refer to users. */
    @Bean
    ModuleMigration workspaceMigration() {
        return new ModuleMigration("workspace", "classpath:db/migration/workspace", 20);
    }
}
