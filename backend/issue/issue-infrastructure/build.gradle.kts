plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:issue:issue-application"))
    implementation(project(":backend:platform"))
}
