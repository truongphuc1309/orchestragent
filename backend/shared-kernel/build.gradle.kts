plugins {
    id("oa.java-library")
}

dependencies {
    // Pure Java module: the only runtime dependency is the UUIDv7 generator.
    api(libs.java.uuid.generator)
}
