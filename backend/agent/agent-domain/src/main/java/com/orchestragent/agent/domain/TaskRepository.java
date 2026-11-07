package com.orchestragent.agent.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    /** Inserts a new task or updates an existing one. */
    void save(AgentTask task);

    /** The task, or empty when the workspace has no task with this ID. */
    Optional<AgentTask> find(UUID workspaceId, TaskId id);

    /** A task by ID alone, for the daemon API (which is not scoped to one workspace). */
    Optional<AgentTask> findById(TaskId id);

    /** Tasks of an issue, newest first, at most {@code limit}. */
    List<AgentTask> findByIssue(UUID workspaceId, UUID issueId, int limit);

    /** Tasks of the issue that are queued, dispatched or running. */
    List<AgentTask> findActiveByIssue(UUID issueId);

    /** Tasks of the agent that are queued, dispatched or running. */
    List<AgentTask> findActiveByAgent(AgentId agentId);

    boolean existsQueuedForIssue(UUID issueId);

    /** True when a task was already made because of this event (handling an event twice makes one task). */
    boolean existsBySourceEvent(UUID eventId);

    /** The most recent completed task of this agent on this issue that left a session behind, if any. */
    Optional<AgentTask> findLastResumable(UUID issueId, AgentId agentId);

    /**
     * Hands out queued tasks to a daemon: at most {@code max} tasks of the given runtimes, oldest first, never
     * two at once for the same issue, and never to two daemons. They come back already dispatched, with a lease
     * that ends at {@code now + AgentTask.LEASE}.
     */
    List<AgentTask> claim(Collection<RuntimeId> runtimeIds, int max, Instant now);

    /** Dispatched tasks whose lease ended before {@code now}. */
    List<AgentTask> findExpiredDispatched(Instant now);

    /** Running tasks of runtimes that are offline. */
    List<AgentTask> findRunningOnOfflineRuntimes();

    /** Running tasks of these runtimes. */
    List<AgentTask> findRunningOnRuntimes(Collection<RuntimeId> runtimeIds);
}
