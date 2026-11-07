package com.orchestragent.workspace.domain;

import org.springframework.util.Assert;

/** Test fixture that violates the rules: a domain class using Spring. */
public class ArchFixtureSpringInDomain {

    void check(Object value) {
        Assert.notNull(value, "value");
    }
}
