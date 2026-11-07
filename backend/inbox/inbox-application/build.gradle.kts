plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:inbox:inbox-domain"))
    api(project(":backend:inbox:inbox-contract"))
    // Only the contract modules of other contexts may be added here, with implementation(...).
    // The events the inbox reacts to, and the names it puts in its notifications.
    implementation(project(":backend:issue:issue-contract"))
    implementation(project(":backend:agent:agent-contract"))
    implementation(project(":backend:identity:identity-contract"))
    implementation(project(":backend:workspace:workspace-contract"))

    // Application layer may only use spring-context and spring-tx annotations.
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
}
