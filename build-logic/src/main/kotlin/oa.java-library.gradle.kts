// Base convention for every JVM module: Java 21 toolchain, Spring Boot BOM for versions, JUnit Jupiter.
plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "com.orchestragent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

dependencies {
    val bom = platform(libs.findLibrary("spring-boot-dependencies").get())
    implementation(bom)
    annotationProcessor(bom)
    testImplementation(bom)

    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked,deprecation"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
