package com.orchestragent.issue.domain;

import java.util.List;

/** One page of issues and how many match in total, so callers can tell when the list was cut short. */
public record IssueList(List<Issue> items, long total) {

    public IssueList {
        items = List.copyOf(items);
    }
}
