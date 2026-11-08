package com.orchestragent.issue.domain;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

public interface IssueRepository {

    /** Inserts a new issue or updates an existing one. */
    void save(Issue issue);

    void delete(Issue issue);

    Optional<Issue> find(UUID workspaceId, IssueId id);

    Optional<Issue> findByNumber(UUID workspaceId, ProjectId projectId, int number);

    /** Newest issue first (latest created), at most {@code limit} items. */
    IssueList list(UUID workspaceId, IssueFilter filter, int limit);

    /** One board column from top to bottom. */
    List<Issue> findByStatus(UUID workspaceId, Status status);

    /** The largest position in the column, or empty when it has no issues. */
    OptionalDouble maxPosition(UUID workspaceId, Status status);
}
