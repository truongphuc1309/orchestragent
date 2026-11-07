plugins {
    id("oa.spring-library")
}

dependencies {
    api(project(":backend:issue:issue-domain"))
    api(project(":backend:issue:issue-contract"))
    // Only the contract modules of other contexts may be added here, with implementation(...).
}
