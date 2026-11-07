package com.orchestragent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Dependency rules of the modular monolith. Package convention: {@code com.orchestragent.<context>.<layer>}
 * with layer one of {@code contract}, {@code domain}, {@code application}, {@code infrastructure}.
 */
final class ArchitectureRules {

    static final List<String> CONTEXTS = List.of("identity", "workspace", "issue", "agent", "inbox");

    private static final String[] FRAMEWORKS = {
        "org.springframework..", "jakarta.persistence..", "org.hibernate..", "com.fasterxml.jackson..", "tools.jackson.."
    };

    private ArchitectureRules() {
    }

    /** Domain is plain Java: no Spring, JPA or JSON library. */
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("com.orchestragent..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true)
            .because("domain modules must stay plain Java");

    /** Domain depends on nothing but shared-kernel. */
    static final ArchRule DOMAIN_DOES_NOT_USE_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("com.orchestragent..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.orchestragent..application..", "com.orchestragent..infrastructure..",
                    "com.orchestragent.platform..")
            .allowEmptyShould(true);

    /** Application layer only uses spring-context and spring-tx, never web, JPA or infrastructure. */
    static final ArchRule APPLICATION_DOES_NOT_USE_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("com.orchestragent..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.orchestragent..infrastructure..", "com.orchestragent.platform..",
                    "org.springframework.web..", "org.springframework.http..", "org.springframework.data..",
                    "org.springframework.jdbc..", "org.springframework.security..",
                    "jakarta.persistence..", "org.hibernate..")
            .allowEmptyShould(true);

    /** Contract is the public API of a context: shared-kernel and plain Java only. */
    static final ArchRule CONTRACT_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("com.orchestragent..contract..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    /** Platform is infrastructure shared by all contexts, so it must not know any of them. */
    static final ArchRule PLATFORM_DOES_NOT_KNOW_CONTEXTS = noClasses()
            .that().resideInAPackage("com.orchestragent.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTEXTS.stream().map(c -> "com.orchestragent." + c + "..").toArray(String[]::new))
            .allowEmptyShould(true);

    /** A context may only use another context through its contract package. */
    static ArchRule contextUsesOthersOnlyThroughContract(String context) {
        String[] otherInternals = CONTEXTS.stream()
                .filter(other -> !other.equals(context))
                .flatMap(other -> List.of("domain", "application", "infrastructure").stream()
                        .map(layer -> "com.orchestragent." + other + "." + layer + ".."))
                .toArray(String[]::new);
        return noClasses()
                .that().resideInAPackage("com.orchestragent." + context + "..")
                .should().dependOnClassesThat().resideInAnyPackage(otherInternals)
                .allowEmptyShould(true)
                .as("Context '" + context + "' must only use other contexts through their contract packages");
    }
}
