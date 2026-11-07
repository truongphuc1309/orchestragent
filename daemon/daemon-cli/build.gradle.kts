plugins {
    id("oa.java-app")
}

dependencies {
    implementation(project(":daemon:daemon-protocol"))
    implementation(project(":daemon:daemon-provider-api"))
    // Providers are discovered at runtime through java.util.ServiceLoader.
    runtimeOnly(project(":daemon:daemon-provider-mock"))
    runtimeOnly(project(":daemon:daemon-provider-claude"))
}

application {
    applicationName = "orchestragent-daemon"
    mainClass = "com.orchestragent.daemon.cli.OrchestragentDaemon"
}
