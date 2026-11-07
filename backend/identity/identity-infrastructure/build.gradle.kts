plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:identity:identity-application"))
    implementation(project(":backend:platform"))
}
