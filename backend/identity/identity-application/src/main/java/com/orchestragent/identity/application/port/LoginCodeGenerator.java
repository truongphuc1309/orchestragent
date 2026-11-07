package com.orchestragent.identity.application.port;

/** Produces the 6-digit codes that are emailed to users. */
public interface LoginCodeGenerator {

    String generate();
}
