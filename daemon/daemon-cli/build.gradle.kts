plugins {
    id("oa.java-app")
}

dependencies {
    implementation(project(":daemon:daemon-protocol"))
    implementation(project(":daemon:daemon-provider-api"))
    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    // Providers are discovered at runtime through java.util.ServiceLoader.
    runtimeOnly(project(":daemon:daemon-provider-mock"))
    runtimeOnly(project(":daemon:daemon-provider-claude"))

    // Tests run whole tasks with the mock provider against a fake server.
    testImplementation(project(":daemon:daemon-provider-mock"))
    testImplementation(libs.awaitility)
}

application {
    applicationName = "orchestragent-daemon"
    mainClass = "com.orchestragent.daemon.cli.OrchestragentDaemon"
}
