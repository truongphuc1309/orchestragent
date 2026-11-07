package com.orchestragent.shared.error;

/**
 * Base class for business-rule violations. The web layer maps each subtype to an HTTP status.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable, machine-readable error code such as {@code workspace.not_member}. */
    public String code() {
        return code;
    }
}
