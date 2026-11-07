package com.orchestragent.shared.error;

/** The caller could not be authenticated (bad credentials, expired or already used code). Maps to 401. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        this("unauthorized", message);
    }

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
