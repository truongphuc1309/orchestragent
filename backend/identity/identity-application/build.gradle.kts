plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:identity:identity-domain"))
    api(project(":backend:identity:identity-contract"))
    // Only the contract modules of other contexts may be added here, with implementation(...).

    // Application layer may only use spring-context and spring-tx annotations.
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
}
