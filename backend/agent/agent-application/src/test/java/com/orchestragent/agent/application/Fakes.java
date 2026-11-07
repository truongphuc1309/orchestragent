package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.agent.domain.RuntimeStatus;
import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory implementations of the ports, so use cases are tested without Spring or a database. */
final class Fakes {

    private Fakes() {
    }

    /** A clock the test moves by hand. */
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

    static final class InMemoryRuntimes implements RuntimeRepository {

        final Map<RuntimeId, Runtime> stored = new LinkedHashMap<>();

        @Override
        public void save(Runtime runtime) {
            stored.put(runtime.id(), runtime);
        }

        @Override
        public Optional<Runtime> find(UUID workspaceId, RuntimeId id) {
            return Optional.ofNullable(stored.get(id)).filter(runtime -> runtime.workspaceId().equals(workspaceId));
        }

        @Override
        public Optional<Runtime> findByDaemon(UUID workspaceId, String daemonId, String provider) {
            return stored.values().stream()
                    .filter(r -> r.workspaceId().equals(workspaceId)
                            && r.daemonId().equals(daemonId)
                            && r.provider().equals(provider))
                    .findFirst();
        }

        @Override
        public List<Runtime> findByWorkspace(UUID workspaceId) {
            return stored.values().stream().filter(r -> r.workspaceId().equals(workspaceId)).toList();
        }

        @Override
        public List<Runtime> findOwnedBy(UUID ownerUserId, Collection<RuntimeId> ids) {
            return stored.values().stream()
                    .filter(r -> r.ownerUserId().equals(ownerUserId) && ids.contains(r.id()))
                    .toList();
        }

        @Override
        public List<Runtime> findOnlineSilentSince(Instant cutoff) {
            return stored.values().stream()
                    .filter(r -> r.status() == RuntimeStatus.ONLINE && r.lastHeartbeatAt().isBefore(cutoff))
                    .toList();
        }
    }

    static final class InMemoryAgents implements AgentRepository {

        final Map<AgentId, Agent> stored = new LinkedHashMap<>();

        @Override
        public void save(Agent agent) {
            stored.put(agent.id(), agent);
        }

        @Override
        public Optional<Agent> find(UUID workspaceId, AgentId id) {
            return Optional.ofNullable(stored.get(id)).filter(agent -> agent.workspaceId().equals(workspaceId));
        }

        @Override
        public List<Agent> findByWorkspace(UUID workspaceId, boolean includeArchived) {
            return stored.values().stream()
                    .filter(a -> a.workspaceId().equals(workspaceId) && (includeArchived || !a.isArchived()))
                    .sorted(Comparator.comparing(a -> a.name().toLowerCase()))
                    .toList();
        }

        @Override
        public List<Agent> findByIds(UUID workspaceId, Collection<AgentId> ids) {
            return stored.values().stream()
                    .filter(a -> a.workspaceId().equals(workspaceId) && ids.contains(a.id()))
                    .toList();
        }

        @Override
        public boolean activeNameExists(UUID workspaceId, String name, AgentId except) {
            return stored.values().stream()
                    .anyMatch(a -> a.workspaceId().equals(workspaceId)
                            && !a.isArchived()
                            && a.name().equalsIgnoreCase(name)
                            && !a.id().equals(except));
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
            Set.copyOf(userIds).forEach(id -> {
                if (byId.containsKey(id)) {
                    result.put(id, byId.get(id));
                }
            });
            return result;
        }
    }
}
