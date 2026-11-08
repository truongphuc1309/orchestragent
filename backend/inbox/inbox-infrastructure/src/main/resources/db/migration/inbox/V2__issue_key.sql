-- Issues are numbered per project since step 13, so a notification keeps a copy of the issue key (WEB-12)
-- instead of the number. Keys never change, so the copy stays true.
ALTER TABLE notifications ADD COLUMN issue_key TEXT;

-- Every issue that existed before projects went to the project "General" with the key GEN, keeping its number
-- (see issue/V3__projects.sql). The same convention gives the old notifications their key without reading the
-- issue schema.
UPDATE notifications SET issue_key = 'GEN-' || issue_number;

ALTER TABLE notifications
    ALTER COLUMN issue_key SET NOT NULL,
    DROP COLUMN issue_number;
