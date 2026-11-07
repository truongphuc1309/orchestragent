package com.orchestragent.shared.error;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        this("forbidden", message);
    }

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
