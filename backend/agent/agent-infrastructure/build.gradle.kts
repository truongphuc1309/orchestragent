plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:agent:agent-application"))
    implementation(project(":backend:platform"))
    // Other contexts are reached only through their contracts (the issue events the task rules listen to, the workspaces the daemon lists).
    implementation(project(":backend:issue:issue-contract"))
    implementation(project(":backend:workspace:workspace-contract"))
    // The wire format shared with the daemon.
    implementation(project(":daemon:daemon-protocol"))
}
