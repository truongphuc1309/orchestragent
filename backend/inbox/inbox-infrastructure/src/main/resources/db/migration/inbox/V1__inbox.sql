-- Notifications of people about issues (step 11). Plain UUIDs for rows of other schemas: no foreign keys across.
CREATE TABLE notifications (
    id                UUID                     NOT NULL PRIMARY KEY,
    workspace_id      UUID                     NOT NULL,
    recipient_user_id UUID                     NOT NULL,
    type              TEXT                     NOT NULL CHECK (type IN ('issue_assigned', 'agent_completed', 'agent_failed')),
    issue_id          UUID                     NOT NULL,
    -- A copy of what the notification says, so it still reads right after the issue or the agent changed.
    issue_number      INTEGER                  NOT NULL,
    issue_title       TEXT                     NOT NULL,
    actor_name        TEXT                     NOT NULL,
    detail            TEXT,
    -- The event that made it. Events are delivered at least once: one event makes one notification per person.
    source_event_id   UUID                     NOT NULL,
    read_at           TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT notifications_one_per_event_and_person UNIQUE (source_event_id, recipient_user_id)
);

CREATE INDEX notifications_inbox_idx ON notifications (workspace_id, recipient_user_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications (workspace_id, recipient_user_id) WHERE read_at IS NULL;
CREATE INDEX notifications_issue_idx ON notifications (issue_id);
