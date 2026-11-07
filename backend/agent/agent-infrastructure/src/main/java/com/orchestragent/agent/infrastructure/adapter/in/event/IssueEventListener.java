package com.orchestragent.agent.infrastructure.adapter.in.event;

import com.orchestragent.agent.application.TaskPlanner;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to what happens on issues and lets {@link TaskPlanner} decide whether an agent gets work. The
 * listener only carries the events over; the rules, and the idempotency that makes it safe when an event is
 * delivered twice, are in the planner. The Modulith event registry stores each event with the change that
 * caused it and delivers again after a crash.
 */
@Component
class IssueEventListener {

    private final TaskPlanner planner;

    IssueEventListener(TaskPlanner planner) {
        this.planner = planner;
    }

    @ApplicationModuleListener
    void on(IssueAssigneeChanged event) {
        planner.assigneeChanged(event);
    }

    @ApplicationModuleListener
    void on(CommentAdded event) {
        planner.commentAdded(event);
    }

    @ApplicationModuleListener
    void on(IssueDeleted event) {
        planner.issueDeleted(event);
    }
}
