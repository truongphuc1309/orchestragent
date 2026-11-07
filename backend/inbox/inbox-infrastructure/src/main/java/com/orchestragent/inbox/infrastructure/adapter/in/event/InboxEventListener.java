package com.orchestragent.inbox.infrastructure.adapter.in.event;

import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.inbox.application.NotificationRules;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Hands the events of the issue and agent contexts to {@link NotificationRules}. The rules are idempotent, as the
 * event registry may deliver an event again.
 */
@Component
class InboxEventListener {

    private final NotificationRules rules;

    InboxEventListener(NotificationRules rules) {
        this.rules = rules;
    }

    @ApplicationModuleListener
    void on(IssueAssigneeChanged event) {
        rules.assigneeChanged(event);
    }

    @ApplicationModuleListener
    void on(AgentTaskCompleted event) {
        rules.taskCompleted(event);
    }

    @ApplicationModuleListener
    void on(AgentTaskFailed event) {
        rules.taskFailed(event);
    }

    @ApplicationModuleListener
    void on(IssueDeleted event) {
        rules.issueDeleted(event);
    }
}
