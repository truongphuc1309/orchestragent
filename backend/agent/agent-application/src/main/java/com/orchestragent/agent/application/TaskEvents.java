package com.orchestragent.agent.application;

import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.agent.contract.AgentTaskMessagesAppended;
import com.orchestragent.agent.contract.AgentTaskStarted;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.shared.id.UuidV7;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes the integration events of the agent context, inside the caller's transaction (so the Modulith
 * event registry stores them together with the change). Every status change publishes {@link AgentTaskChanged}
 * for live views; starting, completing and failing publish a second, typed event for the issue context.
 */
@Component
class TaskEvents {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    TaskEvents(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /** The task was created, handed to a daemon, put back in the queue or cancelled. */
    void changed(AgentTask task) {
        publisher.publishEvent(new AgentTaskChanged(
                UuidV7.next(),
                clock.instant(),
                task.workspaceId(),
                task.issueId(),
                task.id().value(),
                task.status().code()));
    }

    void started(AgentTask task) {
        publisher.publishEvent(new AgentTaskStarted(
                UuidV7.next(),
                clock.instant(),
                task.workspaceId(),
                task.issueId(),
                task.id().value(),
                task.agentId().value()));
        changed(task);
    }

    void completed(AgentTask task) {
        publisher.publishEvent(new AgentTaskCompleted(
                UuidV7.next(),
                clock.instant(),
                task.workspaceId(),
                task.issueId(),
                task.id().value(),
                task.agentId().value(),
                task.output()));
        changed(task);
    }

    void failed(AgentTask task) {
        publisher.publishEvent(new AgentTaskFailed(
                UuidV7.next(),
                clock.instant(),
                task.workspaceId(),
                task.issueId(),
                task.id().value(),
                task.agentId().value(),
                task.failureReason(),
                task.error()));
        changed(task);
    }

    void messages(AgentTask task, long lastSeq) {
        publisher.publishEvent(new AgentTaskMessagesAppended(
                UuidV7.next(), clock.instant(), task.workspaceId(), task.issueId(), task.id().value(), lastSeq));
    }
}
