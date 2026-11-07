CREATE TABLE issues (
    id            UUID                     NOT NULL PRIMARY KEY,
    -- Plain UUIDs of rows in other schemas (workspace, identity, agent): no foreign keys across schemas.
    workspace_id  UUID                     NOT NULL,
    number        INTEGER                  NOT NULL,
    title         TEXT                     NOT NULL,
    description   TEXT                     NOT NULL,
    status        TEXT                     NOT NULL
        CHECK (status IN ('backlog', 'todo', 'in_progress', 'in_review', 'done', 'blocked', 'cancelled')),
    priority      TEXT                     NOT NULL CHECK (priority IN ('none', 'low', 'medium', 'high', 'urgent')),
    assignee_type TEXT                     CHECK (assignee_type IN ('member', 'agent')),
    assignee_id   UUID,
    creator_type  TEXT                     NOT NULL CHECK (creator_type IN ('member', 'agent')),
    creator_id    UUID                     NOT NULL,
    position      DOUBLE PRECISION         NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (workspace_id, number),
    CHECK ((assignee_type IS NULL) = (assignee_id IS NULL))
);

CREATE INDEX issues_board_idx ON issues (workspace_id, status, position);
CREATE INDEX issues_assignee_idx ON issues (workspace_id, assignee_id);

-- One row per workspace; the next issue number is handed out with a single UPDATE ... RETURNING.
CREATE TABLE issue_counters (
    workspace_id UUID    NOT NULL PRIMARY KEY,
    next_number  INTEGER NOT NULL
);

CREATE TABLE comments (
    id           UUID                     NOT NULL PRIMARY KEY,
    workspace_id UUID                     NOT NULL,
    issue_id     UUID                     NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    author_type  TEXT                     NOT NULL CHECK (author_type IN ('member', 'agent', 'system')),
    author_id    UUID,
    body         TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((author_type = 'system') = (author_id IS NULL))
);

CREATE INDEX comments_issue_idx ON comments (issue_id, created_at);
