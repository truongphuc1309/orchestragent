package com.orchestragent.issue.domain;

import java.util.OptionalInt;
import java.util.UUID;

/** Hands out the human-friendly issue numbers (1, 2, 3...) of a project. */
public interface IssueNumbers {

    /**
     * The next number of the project. Concurrent callers never get the same one. Empty when the project does
     * not exist in the workspace or is archived (it may have been archived after the caller loaded it).
     */
    OptionalInt next(UUID workspaceId, ProjectId projectId);
}
