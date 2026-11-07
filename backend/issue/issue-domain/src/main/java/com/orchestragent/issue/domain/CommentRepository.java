package com.orchestragent.issue.domain;

import java.util.List;
import java.util.UUID;

public interface CommentRepository {

    void save(Comment comment);

    /** The comments of an issue, oldest first. */
    List<Comment> findByIssue(UUID workspaceId, IssueId issueId);
}
