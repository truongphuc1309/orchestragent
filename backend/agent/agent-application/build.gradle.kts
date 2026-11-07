plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:agent:agent-domain"))
    api(project(":backend:agent:agent-contract"))
    // Only the contract modules of other contexts may be added here, with implementation(...).
}
