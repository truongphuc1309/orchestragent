package com.orchestragent.platform.security;

/** Names shared by the identity context (which sets the cookie) and the platform (which reads it). */
public final class SessionCookie {

    /** HttpOnly cookie holding the JWT of a browser session. */
    public static final String NAME = "oa_session";

    /** Issuer claim of every JWT issued by this application. */
    public static final String JWT_ISSUER = "orchestragent";

    private SessionCookie() {
    }
}
