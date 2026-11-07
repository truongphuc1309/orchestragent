pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Lets Gradle download a matching JDK when the requested toolchain is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "orchestragent"

// Backend: shared code
include(":backend:shared-kernel")
include(":backend:platform")

// Backend: bounded contexts, each split into the four Clean Architecture layers
val contexts = listOf("identity", "workspace", "issue", "agent", "inbox")
val layers = listOf("contract", "domain", "application", "infrastructure")
contexts.forEach { context ->
    layers.forEach { layer -> include(":backend:$context:$context-$layer") }
}

// Backend: Spring Boot application that wires every context (modular monolith)
include(":backend:app")

// Daemon
include(":daemon:daemon-protocol")
include(":daemon:daemon-provider-api")
include(":daemon:daemon-provider-mock")
include(":daemon:daemon-provider-claude")
include(":daemon:daemon-cli")
