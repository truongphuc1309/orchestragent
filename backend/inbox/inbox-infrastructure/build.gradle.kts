plugins {
    id("oa.spring-library")
}

dependencies {
    implementation(project(":backend:inbox:inbox-application"))
    implementation(project(":backend:platform"))
}
