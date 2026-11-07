package com.orchestragent.daemon.cli;

/** Something the user can fix (a wrong option, no login yet). The command prints the message, without a stack trace. */
public final class UserError extends RuntimeException {

    public UserError(String message) {
        super(message);
    }
}
