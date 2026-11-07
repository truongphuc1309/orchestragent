package com.orchestragent.issue.infrastructure.adapter.in.event;

import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.agent.contract.AgentTaskStarted;
import com.orchestragent.issue.application.AgentRunReactions;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the work of agents and lets {@link AgentRunReactions} update the issue: in progress when a task
 * starts, in review (with the agent's result as a comment) when it completes, blocked (with a system comment)
 * when it fails. The reactions are idempotent, as the event registry may deliver an event again.
 */
@Component
class AgentTaskListener {

    private final AgentRunReactions reactions;

    AgentTaskListener(AgentRunReactions reactions) {
        this.reactions = reactions;
    }

    @ApplicationModuleListener
    void on(AgentTaskStarted event) {
        reactions.taskStarted(event.workspaceId(), event.issueId(), event.agentId());
    }

    @ApplicationModuleListener
    void on(AgentTaskCompleted event) {
        reactions.taskCompleted(event.eventId(), event.workspaceId(), event.issueId(), event.agentId(), event.output());
    }

    @ApplicationModuleListener
    void on(AgentTaskFailed event) {
        reactions.taskFailed(
                event.eventId(), event.workspaceId(), event.issueId(), event.agentId(), event.failureReason(), event.error());
    }
}
