-- A comment an event caused (an agent's result, a failure notice) remembers that event, so handling the same
-- event twice writes the comment once. Comments people write have no source event.
ALTER TABLE comments ADD COLUMN source_event_id UUID;

CREATE UNIQUE INDEX comments_source_event_uidx ON comments (source_event_id) WHERE source_event_id IS NOT NULL;
