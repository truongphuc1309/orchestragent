package com.orchestragent.identity.infrastructure;

import com.orchestragent.identity.application.port.DevLoginCodeProvider;
import com.orchestragent.platform.persistence.ModuleMigration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
class IdentityConfig {

    /** Identity runs first among the contexts: everything else refers to users. */
    @Bean
    ModuleMigration identityMigration() {
        return new ModuleMigration("identity", "classpath:db/migration/identity", 10);
    }

    /**
     * The fixed dev login code, active ONLY in the {@code dev} profile. The configured value is read here and
     * nowhere else, so no other profile can ever see it.
     */
    @Bean
    @Profile("dev")
    DevLoginCodeProvider devLoginCodeProvider(@Value("${orchestragent.dev-login-code:}") String code) {
        return () -> code.isBlank() ? Optional.empty() : Optional.of(code);
    }

    @Bean
    @Profile("!dev")
    DevLoginCodeProvider disabledDevLoginCodeProvider() {
        return Optional::empty;
    }
}
