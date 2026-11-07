plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:inbox:inbox-application"))
    implementation(project(":backend:platform"))
    // The events of other contexts the inbox listens to are reached only through their contracts.
    implementation(project(":backend:issue:issue-contract"))
    implementation(project(":backend:agent:agent-contract"))
}
