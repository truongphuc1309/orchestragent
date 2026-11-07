package com.orchestragent.issue.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous read access to issues for other contexts (the agent context needs the issue and its comments to
 * brief an agent). Becomes an HTTP client if the issue context is split out.
 */
public interface IssueDirectory {

    /** The issue, or empty when the workspace has no issue with this ID (an issue of another workspace counts as none). */
    Optional<IssueDetails> find(UUID workspaceId, UUID issueId);

    /** The comments of an issue, oldest first. Empty when the issue does not exist in the workspace. */
    List<CommentDetails> listComments(UUID workspaceId, UUID issueId);
}
