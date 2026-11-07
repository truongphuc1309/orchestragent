plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:agent:agent-application"))
    implementation(project(":backend:platform"))
}
