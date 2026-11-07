package com.orchestragent.agent.application;

import com.orchestragent.agent.application.TaskViews.TaskMessageView;
import com.orchestragent.agent.application.TaskViews.TaskView;
import com.orchestragent.agent.application.Views.RuntimeSummary;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.agent.domain.TaskId;
import com.orchestragent.agent.domain.TaskMessageRepository;
import com.orchestragent.agent.domain.TaskRepository;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What people do with the tasks of an issue: look at them and their log, cancel a task, retry a failed one.
 * Every method first checks that the caller belongs to the workspace (a non-member gets "not found").
 */
@Service
public class TaskService implements AgentTaskControl {

    /** Most tasks one list call returns; an issue rarely has more runs than this. */
    static final int MAX_TASKS = 50;

    /** Most log lines one call returns; the caller asks again from the last number it got. */
    public static final int MAX_MESSAGES = 500;

    private final TaskRepository tasks;
    private final TaskMessageRepository messages;
    private final AgentRepository agents;
    private final RuntimeRepository runtimes;
    private final IssueDirectory issues;
    private final WorkspaceAccess access;
    private final TaskEvents events;
    private final Clock clock;

    TaskService(
            TaskRepository tasks,
            TaskMessageRepository messages,
            AgentRepository agents,
            RuntimeRepository runtimes,
            IssueDirectory issues,
            WorkspaceAccess access,
            TaskEvents events,
            Clock clock) {
        this.tasks = tasks;
        this.messages = messages;
        this.agents = agents;
        this.runtimes = runtimes;
        this.issues = issues;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /** The runs of an issue, newest first. */
    @Transactional(readOnly = true)
    public List<TaskView> listForIssue(UUID userId, UUID workspaceId, UUID issueId) {
        access.requireMember(workspaceId, userId);
        issues.find(workspaceId, issueId).orElseThrow(TaskService::issueNotFound);
        return views(workspaceId, tasks.findByIssue(workspaceId, issueId, MAX_TASKS));
    }

    /** The log of a task after the given number (-1 for from the start), in order, at most {@link #MAX_MESSAGES}. */
    @Transactional(readOnly = true)
    public List<TaskMessageView> messages(UUID userId, UUID workspaceId, UUID taskId, long afterSeq) {
        access.requireMember(workspaceId, userId);
        AgentTask task = load(workspaceId, taskId);
        return messages.findAfter(task.id(), afterSeq, MAX_MESSAGES).stream()
                .map(message -> new TaskMessageView(message.seq(), message.type(), message.content(), message.createdAt()))
                .toList();
    }

    /** Stops a task that is waiting or running. Cancelling one that already finished changes nothing. */
    @Transactional
    public TaskView cancel(UUID userId, UUID workspaceId, UUID taskId) {
        access.requireMember(workspaceId, userId);
        AgentTask task = load(workspaceId, taskId);
        if (task.cancel(clock.instant())) {
            tasks.save(task);
            events.changed(task);
        }
        return view(task);
    }

    /**
     * Runs a failed task again: a new task with the next attempt number. The agent must still be active and
     * still own the issue, and no other task of the issue may be waiting.
     */
    @Transactional
    public TaskView retry(UUID userId, UUID workspaceId, UUID taskId) {
        access.requireMember(workspaceId, userId);
        AgentTask failed = load(workspaceId, taskId);
        if (failed.status() != TaskStatus.FAILED) {
            throw new ConflictException("task.not_failed", "Only a failed task can be retried");
        }
        Agent agent = agents.find(workspaceId, failed.agentId())
                .orElseThrow(() -> new NotFoundException("agent.not_found", "Agent not found"));
        agent.requireActive();
        IssueDetails issue = issues.find(workspaceId, failed.issueId()).orElseThrow(TaskService::issueNotFound);
        if (issue.assigneeType() != ActorType.AGENT || !agent.id().value().equals(issue.assigneeId())) {
            throw new ConflictException("task.agent_not_assigned", "The issue is not assigned to this agent any more");
        }
        if (tasks.existsQueuedForIssue(failed.issueId())) {
            throw new ConflictException("task.already_queued", "Another run of this issue is already waiting");
        }

        AgentTask retry = AgentTask.queue(
                workspaceId, failed.issueId(), agent.id(), agent.runtimeId(), TaskTrigger.RETRY, null, null,
                failed.attempt() + 1, clock.instant());
        tasks.save(retry);
        events.changed(retry);
        return view(retry);
    }

    // ---- what changing an agent does to its tasks --------------------------------------------------------

    @Override
    @Transactional
    public void cancelAllTasksOf(AgentId agentId) {
        Instant now = clock.instant();
        for (AgentTask task : tasks.findActiveByAgent(agentId)) {
            if (task.cancel(now)) {
                tasks.save(task);
                events.changed(task);
            }
        }
    }

    @Override
    @Transactional
    public void moveQueuedTasks(AgentId agentId, RuntimeId newRuntime) {
        for (AgentTask task : tasks.findActiveByAgent(agentId)) {
            if (task.status() == TaskStatus.QUEUED) {
                task.moveToRuntime(newRuntime);
                tasks.save(task);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private AgentTask load(UUID workspaceId, UUID taskId) {
        return tasks.find(workspaceId, new TaskId(taskId))
                .orElseThrow(() -> new NotFoundException("task.not_found", "Task not found"));
    }

    private TaskView view(AgentTask task) {
        return views(task.workspaceId(), List.of(task)).getFirst();
    }

    private List<TaskView> views(UUID workspaceId, List<AgentTask> list) {
        Map<AgentId, Agent> byAgent = agents.findByIds(workspaceId, list.stream().map(AgentTask::agentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Agent::id, Function.identity()));
        Map<RuntimeId, Runtime> byRuntime = runtimes.findByWorkspace(workspaceId).stream()
                .collect(Collectors.toMap(Runtime::id, Function.identity()));
        return list.stream().map(task -> toView(task, byAgent.get(task.agentId()), byRuntime.get(task.runtimeId()))).toList();
    }

    private static TaskView toView(AgentTask task, Agent agent, Runtime runtime) {
        return new TaskView(
                task.id().value(),
                task.issueId(),
                task.agentId().value(),
                agent == null ? "Unknown agent" : agent.name(),
                runtime == null
                        ? null
                        : new RuntimeSummary(runtime.id().value(), runtime.deviceName(), runtime.provider(), runtime.status()),
                task.trigger(),
                task.status(),
                task.attempt(),
                task.error(),
                task.failureReason(),
                task.usage().inputTokens(),
                task.usage().outputTokens(),
                task.usage().costUsd(),
                task.createdAt(),
                task.startedAt(),
                task.finishedAt());
    }

    private static NotFoundException issueNotFound() {
        return new NotFoundException("issue.not_found", "Issue not found");
    }
}
