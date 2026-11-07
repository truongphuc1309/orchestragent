package com.orchestragent.issue.application;

import com.orchestragent.agent.domain.ArchFixtureTarget;
import com.orchestragent.platform.security.CurrentUser;

/** Test fixture that violates the rules: reaches into another context and into the platform. */
public class ArchFixtureCrossContextUser {

    ArchFixtureTarget target;
    CurrentUser user;
}
