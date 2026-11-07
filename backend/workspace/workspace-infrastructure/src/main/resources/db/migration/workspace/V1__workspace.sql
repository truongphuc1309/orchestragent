CREATE TABLE workspaces (
    id         UUID                     NOT NULL PRIMARY KEY,
    name       TEXT                     NOT NULL,
    slug       TEXT                     NOT NULL UNIQUE,
    -- Plain UUID of a user in the identity schema: no foreign key across schemas.
    created_by UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE members (
    id           UUID                     NOT NULL PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID                     NOT NULL,
    role         TEXT                     NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (workspace_id, user_id)
);

CREATE INDEX members_user_id_idx ON members (user_id);

CREATE TABLE invitations (
    id           UUID                     NOT NULL PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        TEXT                     NOT NULL,
    role         TEXT                     NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    status       TEXT                     NOT NULL CHECK (status IN ('pending', 'accepted', 'revoked')),
    invited_by   UUID                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- At most one pending invitation per address and workspace.
CREATE UNIQUE INDEX invitations_pending_uidx ON invitations (workspace_id, email) WHERE status = 'pending';
CREATE INDEX invitations_pending_email_idx ON invitations (email) WHERE status = 'pending';
