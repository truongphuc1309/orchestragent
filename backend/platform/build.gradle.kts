plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:shared-kernel"))

    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.jdbc)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    api(libs.spring.boot.starter.flyway)
    api(libs.spring.modulith.starter.jpa)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
