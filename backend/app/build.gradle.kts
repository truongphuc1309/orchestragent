plugins {
    id("oa.spring-app")
}

dependencies {
    implementation(project(":backend:platform"))
    implementation(project(":backend:identity:identity-infrastructure"))
    implementation(project(":backend:workspace:workspace-infrastructure"))
    implementation(project(":backend:issue:issue-infrastructure"))
    implementation(project(":backend:agent:agent-infrastructure"))
    implementation(project(":backend:inbox:inbox-infrastructure"))

    // The dev-profile seeder (composition root) calls these use cases directly, like the web API does.
    implementation(project(":backend:identity:identity-application"))
    implementation(project(":backend:workspace:workspace-application"))
    implementation(project(":backend:issue:issue-application"))

    // Integration tests replace ports of the contexts (for example the mail sender) with mocks.
    testImplementation(project(":backend:identity:identity-application"))
    testImplementation(project(":backend:workspace:workspace-application"))
    testImplementation(project(":backend:issue:issue-contract"))
    testImplementation(project(":backend:agent:agent-application"))
    testImplementation(project(":backend:inbox:inbox-application"))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit.junit5)
}

springBoot {
    // Root package, so component, entity and Modulith scanning cover every context.
    mainClass = "com.orchestragent.OrchestragentApplication"
}
