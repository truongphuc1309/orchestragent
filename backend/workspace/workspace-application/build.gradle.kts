plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:workspace:workspace-domain"))
    api(project(":backend:workspace:workspace-contract"))
    // Only the contract modules of other contexts may be added here, with implementation(...).
}
