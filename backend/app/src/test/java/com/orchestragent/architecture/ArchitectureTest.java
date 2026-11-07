package com.orchestragent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.orchestragent", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule domainDoesNotUseOuterLayers = ArchitectureRules.DOMAIN_DOES_NOT_USE_OUTER_LAYERS;

    @ArchTest
    static final ArchRule applicationDoesNotUseInfrastructure = ArchitectureRules.APPLICATION_DOES_NOT_USE_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule contractIsFrameworkFree = ArchitectureRules.CONTRACT_IS_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule platformDoesNotKnowContexts = ArchitectureRules.PLATFORM_DOES_NOT_KNOW_CONTEXTS;

    @ArchTest
    static final ArchRule identityUsesOthersOnlyThroughContract = ArchitectureRules.contextUsesOthersOnlyThroughContract("identity");

    @ArchTest
    static final ArchRule workspaceUsesOthersOnlyThroughContract = ArchitectureRules.contextUsesOthersOnlyThroughContract("workspace");

    @ArchTest
    static final ArchRule issueUsesOthersOnlyThroughContract = ArchitectureRules.contextUsesOthersOnlyThroughContract("issue");

    @ArchTest
    static final ArchRule agentUsesOthersOnlyThroughContract = ArchitectureRules.contextUsesOthersOnlyThroughContract("agent");

    @ArchTest
    static final ArchRule inboxUsesOthersOnlyThroughContract = ArchitectureRules.contextUsesOthersOnlyThroughContract("inbox");
}
