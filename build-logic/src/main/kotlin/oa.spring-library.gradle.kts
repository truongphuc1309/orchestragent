// Convention for modules that use Spring (application and infrastructure layers, platform).
plugins {
    id("oa.java-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    val modulithBom = platform(libs.findLibrary("spring-modulith-bom").get())
    implementation(modulithBom)
    testImplementation(modulithBom)

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
}
