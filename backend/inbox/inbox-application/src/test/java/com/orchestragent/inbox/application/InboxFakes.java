package com.orchestragent.inbox.application;

import com.orchestragent.agent.contract.AgentDirectory;
import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.inbox.contract.InboxChanged;
import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationId;
import com.orchestragent.inbox.domain.NotificationRepository;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentDetails;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;

/** In-memory stand-ins for the ports and the other contexts the inbox uses. */
final class InboxFakes {

    private InboxFakes() {
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Keeps notifications like the database does, including the one-per-event-and-person rule. */
    static final class InMemoryNotifications implements NotificationRepository {
        final List<Notification> all = new ArrayList<>();

        @Override
        public boolean addOnce(Notification n) {
            boolean known = all.stream().anyMatch(o -> o.sourceEventId().equals(n.sourceEventId()) && o.recipientId().equals(n.recipientId()));
            if (known) {
                return false;
            }
            all.add(n);
            return true;
        }

        @Override
        public Optional<Notification> find(UUID workspaceId, UUID recipientId, NotificationId id) {
            return all.stream()
                    .filter(n -> n.id().equals(id) && n.workspaceId().equals(workspaceId) && n.recipientId().equals(recipientId))
                    .findFirst();
        }

        @Override
        public List<Notification> findLatest(UUID workspaceId, UUID recipientId, int limit) {
            return all.stream()
                    .filter(n -> n.workspaceId().equals(workspaceId) && n.recipientId().equals(recipientId))
                    .sorted(Comparator.comparing(Notification::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public int countUnread(UUID workspaceId, UUID recipientId) {
            return (int) all.stream()
                    .filter(n -> n.workspaceId().equals(workspaceId) && n.recipientId().equals(recipientId) && !n.isRead())
                    .count();
        }

        @Override
        public void save(Notification notification) {
            // Objects are kept by reference: nothing to copy.
        }

        @Override
        public int markAllRead(UUID workspaceId, UUID recipientId, Instant now) {
            int changed = 0;
            for (Notification n : all) {
                if (n.workspaceId().equals(workspaceId) && n.recipientId().equals(recipientId) && n.markRead(now)) {
                    changed++;
                }
            }
            return changed;
        }

        @Override
        public List<UUID> deleteByIssue(UUID workspaceId, UUID issueId) {
            Set<UUID> recipients = all.stream()
                    .filter(n -> n.workspaceId().equals(workspaceId) && n.issueId().equals(issueId))
                    .map(Notification::recipientId)
                    .collect(Collectors.toCollection(HashSet::new));
            all.removeIf(n -> n.workspaceId().equals(workspaceId) && n.issueId().equals(issueId));
            return List.copyOf(recipients);
        }
    }

    static final class FakeIssues implements IssueDirectory {
        final Map<UUID, IssueDetails> byId = new HashMap<>();

        IssueDetails add(UUID workspaceId, int number, ActorType creatorType, UUID creatorId) {
            IssueDetails issue = new IssueDetails(UUID.randomUUID(), workspaceId, number, "Issue " + number, "", "todo",
                    null, null, creatorType, creatorId);
            byId.put(issue.id(), issue);
            return issue;
        }

        @Override
        public Optional<IssueDetails> find(UUID workspaceId, UUID issueId) {
            return Optional.ofNullable(byId.get(issueId)).filter(issue -> issue.workspaceId().equals(workspaceId));
        }

        @Override
        public List<CommentDetails> listComments(UUID workspaceId, UUID issueId) {
            return List.of();
        }
    }

    static final class FakeAgents implements AgentDirectory {
        final Map<UUID, AgentSummary> byId = new HashMap<>();

        AgentSummary add(UUID workspaceId, String name) {
            AgentSummary agent = new AgentSummary(UUID.randomUUID(), workspaceId, name, false);
            byId.put(agent.id(), agent);
            return agent;
        }

        @Override
        public Optional<AgentSummary> find(UUID workspaceId, UUID agentId) {
            return Optional.ofNullable(byId.get(agentId)).filter(a -> a.workspaceId().equals(workspaceId));
        }

        @Override
        public Map<UUID, AgentSummary> findByIds(UUID workspaceId, Collection<UUID> agentIds) {
            Map<UUID, AgentSummary> found = new HashMap<>();
            agentIds.forEach(id -> find(workspaceId, id).ifPresent(a -> found.put(id, a)));
            return found;
        }
    }

    static final class FakeUsers implements UserDirectory {
        final Map<UUID, UserView> byId = new HashMap<>();

        UUID add(String name) {
            UserView user = new UserView(UUID.randomUUID(), name.toLowerCase() + "@example.com", name);
            byId.put(user.id(), user);
            return user.id();
        }

        @Override
        public Optional<UserView> findById(UUID userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public Optional<UserView> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Map<UUID, UserView> findByIds(Collection<UUID> userIds) {
            Map<UUID, UserView> found = new HashMap<>();
            userIds.forEach(id -> findById(id).ifPresent(u -> found.put(id, u)));
            return found;
        }
    }

    static final class FakeAccess implements WorkspaceAccess {
        final Map<UUID, Set<UUID>> members = new HashMap<>();

        void join(UUID workspaceId, UUID userId) {
            members.computeIfAbsent(workspaceId, id -> new HashSet<>()).add(userId);
        }

        void leave(UUID workspaceId, UUID userId) {
            members.getOrDefault(workspaceId, new HashSet<>()).remove(userId);
        }

        @Override
        public void requireMember(UUID workspaceId, UUID userId) {
            if (!isMember(workspaceId, userId)) {
                throw new NotFoundException("workspace.not_found", "Workspace not found");
            }
        }

        @Override
        public WorkspaceRole requireRole(UUID workspaceId, UUID userId, WorkspaceRole minimum) {
            requireMember(workspaceId, userId);
            return WorkspaceRole.MEMBER;
        }

        @Override
        public boolean isMember(UUID workspaceId, UUID userId) {
            return members.getOrDefault(workspaceId, Set.of()).contains(userId);
        }

        @Override
        public List<UUID> listMemberIds(UUID workspaceId) {
            return List.copyOf(members.getOrDefault(workspaceId, Set.of()));
        }
    }

    /** Records the inbox changes that were announced. */
    static final class Published extends ArrayList<InboxChanged> implements ApplicationEventPublisher {
        @Override
        public void publishEvent(Object event) {
            if (event instanceof InboxChanged changed) {
                add(changed);
            }
        }

        List<UUID> recipients() {
            return stream().map(InboxChanged::recipientId).toList();
        }
    }
}
