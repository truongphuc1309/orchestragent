CREATE TABLE users (
    id         UUID                     NOT NULL PRIMARY KEY,
    email      TEXT                     NOT NULL UNIQUE,
    name       TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Only a keyed hash of the 6-digit code is stored, never the code itself.
CREATE TABLE login_codes (
    id         UUID                     NOT NULL PRIMARY KEY,
    email      TEXT                     NOT NULL,
    code_hash  TEXT                     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts   INT                      NOT NULL DEFAULT 0,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX login_codes_email_created_at_idx ON login_codes (email, created_at DESC);

-- Only the SHA-256 hash of the token is stored; the raw oat_... value is shown once at creation.
CREATE TABLE api_tokens (
    id           UUID                     NOT NULL PRIMARY KEY,
    user_id      UUID                     NOT NULL REFERENCES users (id),
    name         TEXT                     NOT NULL,
    token_prefix TEXT                     NOT NULL,
    token_hash   TEXT                     NOT NULL UNIQUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX api_tokens_user_id_idx ON api_tokens (user_id);
