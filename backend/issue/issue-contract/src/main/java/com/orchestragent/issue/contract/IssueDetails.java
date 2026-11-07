package com.orchestragent.issue.contract;

import java.util.UUID;

/**
 * An issue as other contexts see it. {@code status} is the lower-case status code (todo, in_progress, ...).
 * {@code assigneeType} is {@code MEMBER} or {@code AGENT}, or null with a null {@code assigneeId} when nobody
 * is assigned. {@code creatorType} and {@code creatorId} say who created the issue (the inbox tells them when an
 * agent finishes or fails).
 */
public record IssueDetails(
        UUID id,
        UUID workspaceId,
        int number,
        String title,
        String description,
        String status,
        ActorType assigneeType,
        UUID assigneeId,
        ActorType creatorType,
        UUID creatorId) {

    /** True for the statuses that end the work: nothing more is expected on the issue. */
    public boolean isClosed() {
        return "done".equals(status) || "cancelled".equals(status);
    }
}
