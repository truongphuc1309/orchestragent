package com.orchestragent.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.domain.ArchFixtureTarget;
import com.orchestragent.issue.application.ArchFixtureCrossContextUser;
import com.orchestragent.workspace.domain.ArchFixtureSpringInDomain;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The real rules pass vacuously while the contexts are still empty. These tests feed them deliberately bad
 * fixture classes to prove that they would fail on a violation.
 */
class ArchitectureRulesAreEffectiveTest {

    private final ClassFileImporter importer = new ClassFileImporter();

    @Test
    void domainRuleRejectsSpringImports() {
        JavaClasses classes = importer.importClasses(ArchFixtureSpringInDomain.class);

        assertThatThrownBy(() -> ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void contextRuleRejectsUsingAnotherContextsInternals() {
        JavaClasses classes = importer.importClasses(ArchFixtureCrossContextUser.class, ArchFixtureTarget.class);

        assertThatThrownBy(() -> ArchitectureRules.contextUsesOthersOnlyThroughContract("issue").check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void applicationRuleRejectsPlatformImports() {
        JavaClasses classes = importer.importClasses(ArchFixtureCrossContextUser.class);

        assertThatThrownBy(() -> ArchitectureRules.APPLICATION_DOES_NOT_USE_INFRASTRUCTURE.check(classes))
                .isInstanceOf(AssertionError.class);
    }
}
