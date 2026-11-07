package com.orchestragent.agent.infrastructure.adapter.out.sse;

import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.contract.AgentTaskMessagesAppended;
import com.orchestragent.platform.sse.SseHub;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the events of the agent context into live events for the browsers watching the workspace. As with
 * issues, the payloads only say what changed ({@code task.updated}: a task changed status; {@code
 * task.message}: its log has new lines) and the client fetches the data again.
 */
@Component
class TaskSseListener {

    static final String TASK_UPDATED = "task.updated";
    static final String TASK_MESSAGE = "task.message";

    record TaskChange(UUID issueId, UUID taskId, String status) {
    }

    record TaskLog(UUID issueId, UUID taskId, long lastSeq) {
    }

    private final SseHub hub;

    TaskSseListener(SseHub hub) {
        this.hub = hub;
    }

    @ApplicationModuleListener
    void on(AgentTaskChanged event) {
        hub.publish(event.workspaceId(), TASK_UPDATED, new TaskChange(event.issueId(), event.taskId(), event.status()));
    }

    @ApplicationModuleListener
    void on(AgentTaskMessagesAppended event) {
        hub.publish(event.workspaceId(), TASK_MESSAGE, new TaskLog(event.issueId(), event.taskId(), event.lastSeq()));
    }
}
