package com.orchestragent.shared.error;

public class ValidationException extends DomainException {

    public ValidationException(String message) {
        this("validation", message);
    }

    public ValidationException(String code, String message) {
        super(code, message);
    }
}
