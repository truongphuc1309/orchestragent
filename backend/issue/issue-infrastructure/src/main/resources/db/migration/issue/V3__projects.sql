-- Projects group the issues of a workspace. An issue belongs to one project and is numbered in it, so its key
-- is the project key and that number (WEB-12). Keys are chosen by people, never change, and are never reused
-- in a workspace (archived projects keep theirs).
CREATE TABLE projects (
    id           UUID                     NOT NULL PRIMARY KEY,
    -- Plain UUIDs of rows in other schemas (workspace, identity): no foreign keys across schemas.
    workspace_id UUID                     NOT NULL,
    key          TEXT                     NOT NULL CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    name         TEXT                     NOT NULL,
    description  TEXT                     NOT NULL,
    created_by   UUID                     NOT NULL,
    archived_at  TIMESTAMP WITH TIME ZONE,
    -- The next issue number of the project, handed out with a single UPDATE ... RETURNING.
    next_number  INTEGER                  NOT NULL DEFAULT 1 CHECK (next_number >= 1),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT projects_workspace_key_key UNIQUE (workspace_id, key),
    -- Target of the issues foreign key below: an issue's project is in the issue's workspace and has its key.
    CONSTRAINT projects_workspace_id_key_key UNIQUE (workspace_id, id, key)
);

-- Issues created before projects existed go to one project per workspace, "General" with the key GEN. They keep
-- their numbers, and the project carries on counting where the workspace counter was. The project is credited
-- to the person who created the first issue (a member when there is one). The same GEN convention lets the inbox
-- migration rewrite its copies of old issue keys without reading this schema.
INSERT INTO projects (id, workspace_id, key, name, description, created_by, archived_at, next_number, created_at, updated_at)
SELECT gen_random_uuid(),
       existing.workspace_id,
       'GEN',
       'General',
       'Issues created before the workspace had projects.',
       existing.first_creator,
       NULL,
       GREATEST(COALESCE(counter.next_number, 1), existing.max_number + 1),
       existing.first_created_at,
       existing.first_created_at
FROM (SELECT workspace_id,
             MIN(created_at)                                                                AS first_created_at,
             MAX(number)                                                                    AS max_number,
             (ARRAY_AGG(creator_id ORDER BY creator_type <> 'member', created_at, id))[1] AS first_creator
      FROM issues
      GROUP BY workspace_id) existing
LEFT JOIN issue_counters counter ON counter.workspace_id = existing.workspace_id;

ALTER TABLE issues
    ADD COLUMN project_id  UUID,
    ADD COLUMN project_key TEXT;

UPDATE issues
SET project_id = projects.id, project_key = projects.key
FROM projects
WHERE projects.workspace_id = issues.workspace_id;

ALTER TABLE issues
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN project_key SET NOT NULL,
    -- The key is copied into the issue because it never changes; the foreign key keeps the copy true.
    ADD CONSTRAINT issues_project_fk FOREIGN KEY (workspace_id, project_id, project_key)
        REFERENCES projects (workspace_id, id, key),
    DROP CONSTRAINT issues_workspace_id_number_key,
    ADD CONSTRAINT issues_project_number_key UNIQUE (project_id, number);

DROP TABLE issue_counters;
