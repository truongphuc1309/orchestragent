-- A runtime is one AI tool (provider) on one computer (daemon) registered with a workspace.
CREATE TABLE runtimes (
    id                UUID                     NOT NULL PRIMARY KEY,
    -- Plain UUIDs of rows in other schemas (workspace, identity): no foreign keys across schemas.
    workspace_id      UUID                     NOT NULL,
    owner_user_id     UUID                     NOT NULL,
    daemon_id         TEXT                     NOT NULL,
    device_name       TEXT                     NOT NULL,
    provider          TEXT                     NOT NULL,
    provider_version  TEXT                     NOT NULL,
    status            TEXT                     NOT NULL CHECK (status IN ('online', 'offline')),
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (workspace_id, daemon_id, provider)
);

CREATE INDEX runtimes_owner_idx ON runtimes (owner_user_id);
-- The job that marks silent runtimes offline only looks at online ones.
CREATE INDEX runtimes_online_heartbeat_idx ON runtimes (last_heartbeat_at) WHERE status = 'online';

CREATE TABLE agents (
    id           UUID                     NOT NULL PRIMARY KEY,
    workspace_id UUID                     NOT NULL,
    name         TEXT                     NOT NULL,
    instructions TEXT                     NOT NULL,
    runtime_id   UUID                     NOT NULL REFERENCES runtimes (id),
    model        TEXT,
    created_by   UUID                     NOT NULL,
    archived_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX agents_workspace_idx ON agents (workspace_id);
-- Active agents of a workspace have distinct names (ignoring case); archived ones may repeat them.
CREATE UNIQUE INDEX agents_active_name_uidx ON agents (workspace_id, lower(name)) WHERE archived_at IS NULL;
