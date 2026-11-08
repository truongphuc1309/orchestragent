package com.orchestragent.issue.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Which issues to list. An empty set means "any" for that field. The assignee filter matches an issue when
 * its assignee is one of {@code assigneeIds}, or when it has none and {@code unassigned} is set.
 */
public record IssueFilter(
        Set<UUID> projectIds, Set<Status> statuses, Set<Priority> priorities, Set<UUID> assigneeIds, boolean unassigned) {

    public IssueFilter {
        projectIds = Set.copyOf(projectIds);
        statuses = Set.copyOf(statuses);
        priorities = Set.copyOf(priorities);
        assigneeIds = Set.copyOf(assigneeIds);
    }

    public static IssueFilter none() {
        return new IssueFilter(Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    public boolean filtersAssignee() {
        return unassigned || !assigneeIds.isEmpty();
    }
}
