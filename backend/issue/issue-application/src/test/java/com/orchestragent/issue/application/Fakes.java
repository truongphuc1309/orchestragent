package com.orchestragent.issue.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.port.AgentDirectory;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueFilter;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueList;
import com.orchestragent.issue.domain.IssueNumbers;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.issue.domain.Project;
import com.orchestragent.issue.domain.ProjectId;
import com.orchestragent.issue.domain.ProjectKey;
import com.orchestragent.issue.domain.ProjectRepository;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

/** In-memory implementations of the ports, so use cases are tested without Spring or a database. */
final class Fakes {

    private Fakes() {
    }

    static Clock clock() {
        return Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    }

    static final class InMemoryIssues implements IssueRepository {

        final Map<IssueId, Issue> stored = new LinkedHashMap<>();

        @Override
        public void save(Issue issue) {
            stored.put(issue.id(), issue);
        }

        @Override
        public void delete(Issue issue) {
            stored.remove(issue.id());
        }

        @Override
        public Optional<Issue> find(UUID workspaceId, IssueId id) {
            return Optional.ofNullable(stored.get(id)).filter(issue -> issue.workspaceId().equals(workspaceId));
        }

        @Override
        public Optional<Issue> findByNumber(UUID workspaceId, ProjectId projectId, int number) {
            return stored.values().stream()
                    .filter(issue -> issue.workspaceId().equals(workspaceId)
                            && issue.projectId().equals(projectId)
                            && issue.number() == number)
                    .findFirst();
        }

        @Override
        public IssueList list(UUID workspaceId, IssueFilter filter, int limit) {
            List<Issue> matching = stored.values().stream()
                    .filter(issue -> issue.workspaceId().equals(workspaceId))
                    .filter(issue -> filter.projectIds().isEmpty() || filter.projectIds().contains(issue.projectId().value()))
                    .filter(issue -> filter.statuses().isEmpty() || filter.statuses().contains(issue.status()))
                    .filter(issue -> filter.priorities().isEmpty() || filter.priorities().contains(issue.priority()))
                    .filter(issue -> !filter.filtersAssignee() || matchesAssignee(issue, filter))
                    .sorted(Comparator.comparing(Issue::createdAt).thenComparing(issue -> issue.id().value()).reversed())
                    .toList();
            return new IssueList(matching.stream().limit(limit).toList(), matching.size());
        }

        private static boolean matchesAssignee(Issue issue, IssueFilter filter) {
            return issue.assignee() == null
                    ? filter.unassigned()
                    : filter.assigneeIds().contains(issue.assignee().id());
        }

        @Override
        public List<Issue> findByStatus(UUID workspaceId, Status status) {
            return stored.values().stream()
                    .filter(issue -> issue.workspaceId().equals(workspaceId) && issue.status() == status)
                    .sorted(Comparator.comparingDouble(Issue::position).thenComparing(Issue::createdAt))
                    .toList();
        }

        @Override
        public OptionalDouble maxPosition(UUID workspaceId, Status status) {
            return findByStatus(workspaceId, status).stream().mapToDouble(Issue::position).max();
        }
    }

    static final class InMemoryComments implements CommentRepository {

        final List<Comment> stored = new ArrayList<>();

        @Override
        public void save(Comment comment) {
            stored.add(comment);
        }

        @Override
        public boolean existsBySourceEvent(UUID eventId) {
            return stored.stream().anyMatch(comment -> eventId.equals(comment.sourceEventId()));
        }

        @Override
        public List<Comment> findByIssue(UUID workspaceId, IssueId issueId) {
            return stored.stream()
                    .filter(c -> c.workspaceId().equals(workspaceId) && c.issueId().equals(issueId))
                    .sorted(Comparator.comparing(Comment::createdAt))
                    .toList();
        }
    }

    static final class InMemoryProjects implements ProjectRepository {

        final Map<ProjectId, Project> stored = new LinkedHashMap<>();
        private final Map<UUID, UUID> defaults = new HashMap<>();

        /** The ID of an active project of the workspace (key {@code WEB}), created on first use. */
        UUID defaultIn(UUID workspaceId) {
            return defaults.computeIfAbsent(workspaceId, ws -> add(ws, "WEB").id().value());
        }

        Project add(UUID workspaceId, String key) {
            Project project = Project.create(
                    workspaceId, new ProjectKey(key), key + " project", "", UUID.randomUUID(), clock().instant());
            save(project);
            return project;
        }

        @Override
        public void save(Project project) {
            stored.put(project.id(), project);
        }

        @Override
        public Optional<Project> find(UUID workspaceId, ProjectId id) {
            return Optional.ofNullable(stored.get(id)).filter(project -> project.workspaceId().equals(workspaceId));
        }

        @Override
        public Optional<Project> findByKey(UUID workspaceId, ProjectKey key) {
            return stored.values().stream()
                    .filter(project -> project.workspaceId().equals(workspaceId) && project.key().equals(key))
                    .findFirst();
        }

        @Override
        public boolean keyExists(UUID workspaceId, ProjectKey key) {
            return findByKey(workspaceId, key).isPresent();
        }

        @Override
        public List<Project> list(UUID workspaceId, boolean includeArchived) {
            return stored.values().stream()
                    .filter(project -> project.workspaceId().equals(workspaceId))
                    .filter(project -> includeArchived || !project.isArchived())
                    .sorted(Comparator.comparing(project -> project.key().value()))
                    .toList();
        }
    }

    /** Counts per project, and like the real counter hands out nothing for an archived or foreign project. */
    static final class InMemoryNumbers implements IssueNumbers {

        private final InMemoryProjects projects;
        private final Map<ProjectId, Integer> last = new HashMap<>();

        InMemoryNumbers(InMemoryProjects projects) {
            this.projects = projects;
        }

        @Override
        public OptionalInt next(UUID workspaceId, ProjectId projectId) {
            Optional<Project> project = projects.find(workspaceId, projectId).filter(found -> !found.isArchived());
            return project.isEmpty() ? OptionalInt.empty() : OptionalInt.of(last.merge(projectId, 1, Integer::sum));
        }
    }

    /** Membership as the workspace context would answer it. */
    static final class FakeAccess implements WorkspaceAccess {

        private final Map<UUID, Map<UUID, WorkspaceRole>> roles = new HashMap<>();

        void join(UUID workspaceId, UUID userId, WorkspaceRole role) {
            roles.computeIfAbsent(workspaceId, id -> new LinkedHashMap<>()).put(userId, role);
        }

        @Override
        public void requireMember(UUID workspaceId, UUID userId) {
            requireRole(workspaceId, userId, WorkspaceRole.MEMBER);
        }

        @Override
        public WorkspaceRole requireRole(UUID workspaceId, UUID userId, WorkspaceRole minimum) {
            WorkspaceRole actual = roles.getOrDefault(workspaceId, Map.of()).get(userId);
            if (actual == null) {
                throw new NotFoundException("workspace.not_found", "Workspace not found");
            }
            if (!actual.atLeast(minimum)) {
                throw new ForbiddenException("workspace.role_required", "Role too low");
            }
            return actual;
        }

        @Override
        public boolean isMember(UUID workspaceId, UUID userId) {
            return roles.getOrDefault(workspaceId, Map.of()).containsKey(userId);
        }

        @Override
        public List<UUID> listMemberIds(UUID workspaceId) {
            return List.copyOf(roles.getOrDefault(workspaceId, Map.of()).keySet());
        }
    }

    static final class FakeUsers implements UserDirectory {

        final Map<UUID, UserView> byId = new HashMap<>();

        UserView add(String name) {
            UserView user = new UserView(UUID.randomUUID(), name.toLowerCase() + "@example.com", name);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<UserView> findById(UUID userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public Optional<UserView> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.email().equalsIgnoreCase(email.trim())).findFirst();
        }

        @Override
        public Map<UUID, UserView> findByIds(Collection<UUID> userIds) {
            Map<UUID, UserView> result = new HashMap<>();
            userIds.forEach(id -> {
                if (byId.containsKey(id)) {
                    result.put(id, byId.get(id));
                }
            });
            return result;
        }
    }

    /** Agents that exist in a workspace, as the agent context will answer once it exists. */
    static final class FakeAgents implements AgentDirectory {

        final Map<UUID, String> byId = new HashMap<>();

        UUID add(String name) {
            UUID id = UUID.randomUUID();
            byId.put(id, name);
            return id;
        }

        @Override
        public boolean exists(UUID workspaceId, UUID agentId) {
            return byId.containsKey(agentId);
        }

        @Override
        public Map<UUID, String> names(UUID workspaceId, Collection<UUID> agentIds) {
            Map<UUID, String> result = new HashMap<>();
            agentIds.forEach(id -> {
                if (byId.containsKey(id)) {
                    result.put(id, byId.get(id));
                }
            });
            return result;
        }
    }
}
