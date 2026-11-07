package com.orchestragent.issue.domain;

import java.util.List;
import java.util.UUID;

public interface CommentRepository {

    void save(Comment comment);

    /** True when a comment was already written because of this event. */
    boolean existsBySourceEvent(UUID eventId);

    /** The comments of an issue, oldest first. */
    List<Comment> findByIssue(UUID workspaceId, IssueId issueId);
}
