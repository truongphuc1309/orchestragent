package com.orchestragent.platform.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    static final String RUNNER_BEAN = "moduleFlywayRunner";

    /** Order 0: the Modulith event publication table must exist before any context uses events. */
    @Bean
    ModuleMigration platformMigration() {
        return new ModuleMigration("public", "classpath:db/migration/platform", 0);
    }

    @Bean(RUNNER_BEAN)
    ModuleFlywayRunner moduleFlywayRunner(DataSource dataSource, List<ModuleMigration> migrations) {
        return new ModuleFlywayRunner(dataSource, migrations);
    }

    /** Makes sure all migrations are applied before JPA (and with it the event registry) starts. */
    @Bean
    static BeanFactoryPostProcessor entityManagerFactoryDependsOnMigrations() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanNamesForType(EntityManagerFactory.class, true, false)) {
                BeanDefinition definition = beanFactory.getBeanDefinition(name);
                List<String> dependsOn = new ArrayList<>(
                        definition.getDependsOn() == null ? List.of() : Arrays.asList(definition.getDependsOn()));
                if (!dependsOn.contains(RUNNER_BEAN)) {
                    dependsOn.add(RUNNER_BEAN);
                    definition.setDependsOn(dependsOn.toArray(String[]::new));
                }
            }
        };
    }
}
