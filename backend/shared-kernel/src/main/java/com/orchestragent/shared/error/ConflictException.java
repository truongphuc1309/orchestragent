package com.orchestragent.shared.error;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        this("conflict", message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
