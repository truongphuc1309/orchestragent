package com.orchestragent.issue.domain;

import java.util.UUID;

/** Hands out the human-friendly issue numbers (1, 2, 3...) of a workspace. */
public interface IssueNumbers {

    /** The next number of the workspace. Concurrent callers never get the same one. */
    int next(UUID workspaceId);
}
