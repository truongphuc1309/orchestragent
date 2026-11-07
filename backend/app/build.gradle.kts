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
}

springBoot {
    // Root package, so component, entity and Modulith scanning cover every context.
    mainClass = "com.orchestragent.OrchestragentApplication"
}
