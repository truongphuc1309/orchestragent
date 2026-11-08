package com.orchestragent.issue.application;

import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Status;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models returned by the use cases. */
public final class Views {

    private Views() {
    }

    /**
     * A member, an agent or the system, with a display name. {@code id} is null for the system. The name is
     * a placeholder such as "Unknown user" when the account can no longer be found.
     */
    public record ActorView(ActorType type, UUID id, String name) {
    }

    /** {@code key} is {@code WEB-12}: the key of the project and {@code number}. */
    public record IssueView(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            String key,
            int number,
            String title,
            String description,
            Status status,
            Priority priority,
            ActorView assignee,
            ActorView creator,
            double position,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** {@code total} counts all matching issues, so a client can tell when {@code items} was cut short. */
    public record IssuePageView(List<IssueView> items, long total) {
    }

    /** {@code createdByName} is a placeholder such as "Unknown user" when the account can no longer be found. */
    public record ProjectView(
            UUID id,
            UUID workspaceId,
            String key,
            String name,
            String description,
            UUID createdBy,
            String createdByName,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {

        public boolean archived() {
            return archivedAt != null;
        }
    }

    public record CommentView(UUID id, UUID issueId, ActorView author, String body, Instant createdAt) {
    }
}
