package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.TaskRepository;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides when an agent gets work, from what happens on issues. These are the rules:
 *
 * <ul>
 *   <li>An issue is assigned to an agent, and is not done or cancelled: a task is queued (trigger
 *       {@code assigned}).
 *   <li>A person comments on an issue assigned to an agent: a task is queued (trigger {@code comment}), unless
 *       one is already waiting, because that one will read every comment anyway.
 *   <li>A reviewer sends the issue back with a comment: same, with trigger {@code changes_requested}.
 *   <li>A comment of an agent or of the system never starts work, so agents cannot trigger each other in a loop.
 *   <li>An issue is unassigned from an agent, reassigned, or deleted: that agent's tasks on it are cancelled.
 * </ul>
 *
 * The events arrive at least once, so every method is idempotent: a task remembers the event it came from.
 */
@Service
public class TaskPlanner {

    private final TaskRepository tasks;
    private final AgentRepository agents;
    private final IssueDirectory issues;
    private final TaskEvents events;
    private final Clock clock;

    TaskPlanner(TaskRepository tasks, AgentRepository agents, IssueDirectory issues, TaskEvents events, Clock clock) {
        this.tasks = tasks;
        this.agents = agents;
        this.issues = issues;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void assigneeChanged(IssueAssigneeChanged event) {
        UUID newAgent = event.newAssigneeType() == ActorType.AGENT ? event.newAssigneeId() : null;

        // Whoever else was working on the issue stops: the issue now belongs to someone else (or to nobody).
        Instant now = clock.instant();
        for (AgentTask task : tasks.findActiveByIssue(event.issueId())) {
            if (!task.agentId().value().equals(newAgent) && task.cancel(now)) {
                tasks.save(task);
                events.changed(task);
            }
        }

        if (newAgent == null || tasks.existsBySourceEvent(event.eventId())) {
            return;
        }
        Optional<IssueDetails> issue = issues.find(event.workspaceId(), event.issueId());
        // The event may be old: only act when the issue is still assigned to this agent and still open.
        if (issue.isEmpty() || issue.get().isClosed() || !isAssignedTo(issue.get(), newAgent)) {
            return;
        }
        activeAgent(event.workspaceId(), newAgent).ifPresent(agent -> {
            if (!tasks.existsQueuedForIssue(event.issueId())) {
                queue(agent, event.issueId(), TaskTrigger.ASSIGNED, null, event.eventId(), now);
            }
        });
    }

    @Transactional
    public void commentAdded(CommentAdded event) {
        if (event.authorType() != ActorType.MEMBER || tasks.existsBySourceEvent(event.eventId())) {
            return;
        }
        Optional<IssueDetails> issue = issues.find(event.workspaceId(), event.issueId());
        if (issue.isEmpty() || issue.get().assigneeType() != ActorType.AGENT) {
            return;
        }
        activeAgent(event.workspaceId(), issue.get().assigneeId()).ifPresent(agent -> {
            // A waiting task will read all comments, this one included.
            if (!tasks.existsQueuedForIssue(event.issueId())) {
                TaskTrigger trigger = event.changesRequested() ? TaskTrigger.CHANGES_REQUESTED : TaskTrigger.COMMENT;
                queue(agent, event.issueId(), trigger, event.commentId(), event.eventId(), clock.instant());
            }
        });
    }

    @Transactional
    public void issueDeleted(IssueDeleted event) {
        Instant now = clock.instant();
        for (AgentTask task : tasks.findActiveByIssue(event.issueId())) {
            if (task.cancel(now)) {
                tasks.save(task);
                events.changed(task);
            }
        }
    }

    private void queue(Agent agent, UUID issueId, TaskTrigger trigger, UUID commentId, UUID eventId, Instant now) {
        AgentTask task = AgentTask.queue(
                agent.workspaceId(), issueId, agent.id(), agent.runtimeId(), trigger, commentId, eventId, 1, now);
        tasks.save(task);
        events.changed(task);
    }

    private Optional<Agent> activeAgent(UUID workspaceId, UUID agentId) {
        return agents.find(workspaceId, new AgentId(agentId)).filter(agent -> !agent.isArchived());
    }

    private static boolean isAssignedTo(IssueDetails issue, UUID agentId) {
        return issue.assigneeType() == ActorType.AGENT && agentId.equals(issue.assigneeId());
    }
}
