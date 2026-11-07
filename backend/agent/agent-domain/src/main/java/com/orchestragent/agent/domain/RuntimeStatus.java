package com.orchestragent.agent.domain;

import java.util.Locale;

public enum RuntimeStatus {
    ONLINE,
    OFFLINE;

    /** The lower-case code used in the database and the API. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RuntimeStatus fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
