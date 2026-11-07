package com.orchestragent.issue.infrastructure.adapter.out.sse;

import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueCreated;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueUpdated;
import com.orchestragent.platform.sse.SseHub;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the integration events of the issue context into live events for the browsers watching the
 * workspace. The payloads are deliberately small: they say what changed, and the client fetches the data
 * again, so a missed or repeated event does no harm (which also makes this listener idempotent).
 */
@Component
class IssueSseListener {

    static final String ISSUE_CREATED = "issue.created";
    static final String ISSUE_UPDATED = "issue.updated";
    static final String ISSUE_DELETED = "issue.deleted";
    static final String COMMENT_CREATED = "comment.created";

    record IssueRef(UUID issueId, int number) {
    }

    record IssueChange(UUID issueId, Set<String> changedFields) {
    }

    record CommentRef(UUID issueId, UUID commentId) {
    }

    private final SseHub hub;

    IssueSseListener(SseHub hub) {
        this.hub = hub;
    }

    @ApplicationModuleListener
    void on(IssueCreated event) {
        hub.publish(event.workspaceId(), ISSUE_CREATED, new IssueRef(event.issueId(), event.number()));
    }

    @ApplicationModuleListener
    void on(IssueUpdated event) {
        hub.publish(event.workspaceId(), ISSUE_UPDATED, new IssueChange(event.issueId(), event.changedFields()));
    }

    /** The browser only needs to know that something about the issue changed. */
    @ApplicationModuleListener
    void on(IssueAssigneeChanged event) {
        hub.publish(event.workspaceId(), ISSUE_UPDATED, new IssueChange(event.issueId(), Set.of("assignee")));
    }

    @ApplicationModuleListener
    void on(IssueDeleted event) {
        hub.publish(event.workspaceId(), ISSUE_DELETED, new IssueRef(event.issueId(), event.number()));
    }

    @ApplicationModuleListener
    void on(CommentAdded event) {
        hub.publish(event.workspaceId(), COMMENT_CREATED, new CommentRef(event.issueId(), event.commentId()));
    }
}
