-- One run of an agent on an issue: the unit a daemon claims and works on.
CREATE TABLE agent_tasks (
    id                 UUID                     NOT NULL PRIMARY KEY,
    -- Plain UUIDs of rows in the workspace and issue schemas: no foreign keys across schemas.
    workspace_id       UUID                     NOT NULL,
    issue_id           UUID                     NOT NULL,
    agent_id           UUID                     NOT NULL REFERENCES agents (id),
    runtime_id         UUID                     NOT NULL REFERENCES runtimes (id),
    trigger            TEXT                     NOT NULL CHECK (trigger IN ('assigned', 'comment', 'changes_requested', 'retry')),
    trigger_comment_id UUID,
    -- The event this task was made for. Handling the same event twice must make one task: NULL (a person asked
    -- directly, as with a retry) is allowed any number of times, every other value only once.
    source_event_id    UUID UNIQUE,
    status             TEXT                     NOT NULL
        CHECK (status IN ('queued', 'dispatched', 'running', 'completed', 'failed', 'cancelled')),
    attempt            INTEGER                  NOT NULL CHECK (attempt >= 1),
    session_id         TEXT,
    work_dir           TEXT,
    output             TEXT,
    error              TEXT,
    failure_reason     TEXT,
    input_tokens       BIGINT,
    output_tokens      BIGINT,
    cost_usd           NUMERIC(12, 6),
    lease_expires_at   TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at         TIMESTAMP WITH TIME ZONE,
    finished_at        TIMESTAMP WITH TIME ZONE,
    -- Optimistic locking: two requests changing the same task (cancel and complete) cannot overwrite each other.
    version            BIGINT                   NOT NULL DEFAULT 0
);

-- An issue has at most one task waiting. A comment that arrives while one waits is read by that task.
CREATE UNIQUE INDEX agent_tasks_one_queued_per_issue_uidx ON agent_tasks (issue_id) WHERE status = 'queued';
-- What a daemon polling for work looks at.
CREATE INDEX agent_tasks_claim_idx ON agent_tasks (runtime_id, created_at) WHERE status = 'queued';
-- The job that takes back tasks whose lease ran out.
CREATE INDEX agent_tasks_lease_idx ON agent_tasks (lease_expires_at) WHERE status = 'dispatched';
CREATE INDEX agent_tasks_issue_idx ON agent_tasks (issue_id, created_at);
CREATE INDEX agent_tasks_active_agent_idx ON agent_tasks (agent_id) WHERE status IN ('queued', 'dispatched', 'running');
CREATE INDEX agent_tasks_running_runtime_idx ON agent_tasks (runtime_id) WHERE status = 'running';

-- The log of a task, line by line.
CREATE TABLE task_messages (
    id         UUID                     NOT NULL PRIMARY KEY,
    task_id    UUID                     NOT NULL REFERENCES agent_tasks (id) ON DELETE CASCADE,
    seq        BIGINT                   NOT NULL CHECK (seq >= 0),
    type       TEXT                     NOT NULL CHECK (type IN ('text', 'tool_use', 'tool_result', 'error', 'system')),
    content    TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- A daemon may resend a batch: a number stored once is never stored again.
    UNIQUE (task_id, seq)
);
