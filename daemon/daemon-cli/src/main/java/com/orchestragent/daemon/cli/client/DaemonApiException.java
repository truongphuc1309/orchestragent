package com.orchestragent.daemon.cli.client;

/**
 * A call to the server failed. {@code status} is the HTTP status, or 0 when no answer came (network down, server
 * not running, timeout). {@code code} is the error code of the server's problem detail (such as
 * {@code task.lease_lost}), or null.
 */
public final class DaemonApiException extends RuntimeException {

    private final int status;
    private final String code;

    public DaemonApiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public DaemonApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Worth trying again later: no answer, a timeout, too many requests, or a server error. */
    public boolean isRetryable() {
        return status == 0 || status == 408 || status == 429 || status >= 500;
    }

    /** The token was refused (missing, wrong or revoked): nothing will work until the user logs in again. */
    public boolean isUnauthorized() {
        return status == 401;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isConflict() {
        return status == 409;
    }
}
