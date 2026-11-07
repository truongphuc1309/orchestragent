package com.orchestragent.shared.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        this("not_found", message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
