plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:issue:issue-application"))
    implementation(project(":backend:platform"))
    // Another context is reached only through its contract.
    implementation(project(":backend:agent:agent-contract"))
}
