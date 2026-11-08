package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeStatus;
import com.orchestragent.agent.domain.TaskId;
import com.orchestragent.agent.domain.TaskMessage;
import com.orchestragent.agent.domain.TaskMessageRepository;
import com.orchestragent.agent.domain.TaskRepository;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentDetails;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

/** In-memory ports for the task use cases. */
final class TaskFakes {

    private TaskFakes() {
    }

    /** Collects every published event, in order. */
    static final class Published extends ArrayList<Object> implements ApplicationEventPublisher {

        @Override
        public void publishEvent(Object event) {
            add(event);
        }

        <T> List<T> of(Class<T> type) {
            return stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    static final class RecordingTaskControl implements AgentTaskControl {

        final List<AgentId> cancelled = new ArrayList<>();
        final Map<AgentId, RuntimeId> moved = new HashMap<>();

        @Override
        public void cancelAllTasksOf(AgentId agentId) {
            cancelled.add(agentId);
        }

        @Override
        public void moveQueuedTasks(AgentId agentId, RuntimeId newRuntime) {
            moved.put(agentId, newRuntime);
        }
    }

    /** Stores tasks in memory and hands them out like the real claim does (never twice, one per issue at a time). */
    static final class InMemoryTasks implements TaskRepository {

        final Map<TaskId, AgentTask> stored = new LinkedHashMap<>();
        private final InMemoryRuntimesView runtimes;

        InMemoryTasks(InMemoryRuntimesView runtimes) {
            this.runtimes = runtimes;
        }

        @Override
        public void save(AgentTask task) {
            stored.put(task.id(), task);
        }

        @Override
        public Optional<AgentTask> find(UUID workspaceId, TaskId id) {
            return Optional.ofNullable(stored.get(id)).filter(task -> task.workspaceId().equals(workspaceId));
        }

        @Override
        public Optional<AgentTask> findById(TaskId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<AgentTask> findByIssue(UUID workspaceId, UUID issueId, int limit) {
            return stored.values().stream()
                    .filter(task -> task.workspaceId().equals(workspaceId) && task.issueId().equals(issueId))
                    .sorted(Comparator.comparing(AgentTask::createdAt).thenComparing(task -> task.id().value()).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<AgentTask> findActiveByIssue(UUID issueId) {
            return stored.values().stream()
                    .filter(task -> task.issueId().equals(issueId) && task.isActive())
                    .toList();
        }

        @Override
        public List<AgentTask> findActiveByAgent(AgentId agentId) {
            return stored.values().stream()
                    .filter(task -> task.agentId().equals(agentId) && task.isActive())
                    .toList();
        }

        @Override
        public boolean existsQueuedForIssue(UUID issueId) {
            return stored.values().stream()
                    .anyMatch(task -> task.issueId().equals(issueId) && task.status() == TaskStatus.QUEUED);
        }

        @Override
        public boolean existsBySourceEvent(UUID eventId) {
            return stored.values().stream().anyMatch(task -> eventId.equals(task.sourceEventId()));
        }

        @Override
        public Optional<AgentTask> findLastResumable(UUID issueId, AgentId agentId) {
            return stored.values().stream()
                    .filter(task -> task.issueId().equals(issueId)
                            && task.agentId().equals(agentId)
                            && task.status() == TaskStatus.COMPLETED
                            && task.sessionId() != null)
                    .max(Comparator.comparing(AgentTask::finishedAt));
        }

        @Override
        public List<AgentTask> claim(Collection<RuntimeId> runtimeIds, int max, Instant now) {
            List<AgentTask> picked = new ArrayList<>();
            List<AgentTask> candidates = stored.values().stream()
                    .filter(task -> task.status() == TaskStatus.QUEUED && runtimeIds.contains(task.runtimeId()))
                    .sorted(Comparator.comparing(AgentTask::createdAt))
                    .toList();
            for (AgentTask task : candidates) {
                boolean issueBusy = stored.values().stream()
                        .anyMatch(other -> other.issueId().equals(task.issueId())
                                && (other.status() == TaskStatus.DISPATCHED || other.status() == TaskStatus.RUNNING));
                if (!issueBusy && picked.size() < max) {
                    task.dispatch(now);
                    picked.add(task);
                }
            }
            return picked;
        }

        @Override
        public List<AgentTask> findExpiredDispatched(Instant now) {
            return stored.values().stream()
                    .filter(task -> task.status() == TaskStatus.DISPATCHED && task.leaseExpiresAt().isBefore(now))
                    .toList();
        }

        @Override
        public List<AgentTask> findRunningOnOfflineRuntimes() {
            return stored.values().stream()
                    .filter(task -> task.status() == TaskStatus.RUNNING
                            && runtimes.status(task.runtimeId()) == RuntimeStatus.OFFLINE)
                    .toList();
        }

        @Override
        public List<AgentTask> findRunningOnRuntimes(Collection<RuntimeId> runtimeIds) {
            return stored.values().stream()
                    .filter(task -> task.status() == TaskStatus.RUNNING && runtimeIds.contains(task.runtimeId()))
                    .toList();
        }
    }

    /** The part of the runtime store the task fake needs: the status of a runtime. */
    interface InMemoryRuntimesView {

        RuntimeStatus status(RuntimeId id);
    }

    static InMemoryRuntimesView statusOf(Fakes.InMemoryRuntimes runtimes) {
        return id -> runtimes.stored.get(id).status();
    }

    static final class InMemoryMessages implements TaskMessageRepository {

        private final Map<TaskId, TreeMap<Long, TaskMessage>> stored = new HashMap<>();

        @Override
        public int append(TaskId taskId, List<TaskMessage> messages) {
            TreeMap<Long, TaskMessage> byNumber = stored.computeIfAbsent(taskId, id -> new TreeMap<>());
            int added = 0;
            for (TaskMessage message : messages) {
                if (byNumber.putIfAbsent(message.seq(), message) == null) {
                    added++;
                }
            }
            return added;
        }

        @Override
        public List<TaskMessage> findAfter(TaskId taskId, long afterSeq, int limit) {
            return stored.getOrDefault(taskId, new TreeMap<>()).tailMap(afterSeq, false).values().stream()
                    .limit(limit)
                    .toList();
        }

        @Override
        public OptionalLong lastSeq(TaskId taskId) {
            TreeMap<Long, TaskMessage> byNumber = stored.get(taskId);
            return byNumber == null || byNumber.isEmpty() ? OptionalLong.empty() : OptionalLong.of(byNumber.lastKey());
        }
    }

    /** The issue context as the agent context sees it. */
    static final class FakeIssues implements IssueDirectory {

        /** Every fake issue is in one project with the key WEB, so issue 7 is WEB-7. */
        static final UUID PROJECT_ID = UUID.randomUUID();

        final Map<UUID, IssueDetails> byId = new HashMap<>();
        final Map<UUID, List<CommentDetails>> comments = new HashMap<>();

        IssueDetails add(UUID workspaceId, int number, ActorType assigneeType, UUID assigneeId, String status) {
            IssueDetails issue = new IssueDetails(
                    UUID.randomUUID(), workspaceId, PROJECT_ID, "WEB-" + number, number, "Issue " + number, "Description " + number, status, assigneeType, assigneeId,
                    ActorType.MEMBER, UUID.randomUUID());
            byId.put(issue.id(), issue);
            return issue;
        }

        IssueDetails assign(IssueDetails issue, ActorType type, UUID assigneeId) {
            IssueDetails changed = new IssueDetails(
                    issue.id(), issue.workspaceId(), issue.projectId(), issue.key(), issue.number(), issue.title(), issue.description(),
                    issue.status(), type, assigneeId,
                    issue.creatorType(), issue.creatorId());
            byId.put(issue.id(), changed);
            return changed;
        }

        IssueDetails withStatus(IssueDetails issue, String status) {
            IssueDetails changed = new IssueDetails(
                    issue.id(), issue.workspaceId(), issue.projectId(), issue.key(), issue.number(), issue.title(), issue.description(), status,
                    issue.assigneeType(), issue.assigneeId(), issue.creatorType(), issue.creatorId());
            byId.put(issue.id(), changed);
            return changed;
        }

        void comment(IssueDetails issue, ActorType type, String authorName, String body, Instant at) {
            comments.computeIfAbsent(issue.id(), id -> new ArrayList<>())
                    .add(new CommentDetails(UUID.randomUUID(), type, type == ActorType.SYSTEM ? null : UUID.randomUUID(), authorName, body, at));
        }

        @Override
        public Optional<IssueDetails> find(UUID workspaceId, UUID issueId) {
            return Optional.ofNullable(byId.get(issueId)).filter(issue -> issue.workspaceId().equals(workspaceId));
        }

        @Override
        public List<CommentDetails> listComments(UUID workspaceId, UUID issueId) {
            return find(workspaceId, issueId).isEmpty() ? List.of() : comments.getOrDefault(issueId, List.of());
        }
    }

    static Runtime runtime(Fakes.InMemoryRuntimes runtimes, UUID workspaceId, UUID owner, String daemon, Instant now) {
        Runtime runtime = Runtime.register(workspaceId, owner, daemon, "Device " + daemon, "claude", "1.0", now);
        runtimes.save(runtime);
        return runtime;
    }
}
