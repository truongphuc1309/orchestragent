package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.RuntimeId;

/** What changing an agent does to its tasks. Implemented by {@link TaskService}; the agent service calls it. */
interface AgentTaskControl {

    /** The agent was archived: stop all its work, queued and running. */
    void cancelAllTasksOf(AgentId agentId);

    /** The agent moved to another runtime: its waiting tasks follow it. */
    void moveQueuedTasks(AgentId agentId, RuntimeId newRuntime);
}
