plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:workspace:workspace-application"))
    implementation(project(":backend:platform"))
}
