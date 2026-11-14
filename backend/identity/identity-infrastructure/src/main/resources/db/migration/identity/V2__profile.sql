-- Profile of a user: "About you" text and an avatar picture. The picture itself is in object storage; the row keeps
-- its ID (a new one for every upload) and its format.
ALTER TABLE identity.users
    ADD COLUMN bio         TEXT NOT NULL DEFAULT '',
    ADD COLUMN avatar_id   UUID,
    ADD COLUMN avatar_type TEXT,
    ADD CONSTRAINT users_avatar_complete CHECK ((avatar_id IS NULL) = (avatar_type IS NULL)),
    ADD CONSTRAINT users_avatar_type CHECK (avatar_type IN ('PNG', 'JPEG', 'WEBP'));
