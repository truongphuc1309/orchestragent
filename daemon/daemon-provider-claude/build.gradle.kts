plugins {
    id("oa.java-library")
}

dependencies {
    implementation(project(":daemon:daemon-provider-api"))
    implementation(libs.jackson.databind)

    testImplementation(libs.awaitility)
}
