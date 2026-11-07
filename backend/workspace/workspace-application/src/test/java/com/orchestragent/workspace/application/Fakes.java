package com.orchestragent.workspace.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.workspace.application.port.InvitationNotifier;
import com.orchestragent.workspace.domain.Email;
import com.orchestragent.workspace.domain.Invitation;
import com.orchestragent.workspace.domain.InvitationId;
import com.orchestragent.workspace.domain.InvitationRepository;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.Workspace;
import com.orchestragent.workspace.domain.WorkspaceId;
import com.orchestragent.workspace.domain.WorkspaceRepository;
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
import java.util.UUID;

/** In-memory implementations of the ports, so use cases are tested without Spring or a database. */
final class Fakes {

    private Fakes() {
    }

    static Clock clock() {
        return Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    }

    static final class InMemoryMembers implements MemberRepository {

        final List<Member> stored = new ArrayList<>();

        @Override
        public void save(Member member) {
            if (!stored.contains(member)) {
                stored.add(member);
            }
        }

        @Override
        public void delete(Member member) {
            stored.remove(member);
        }

        @Override
        public Optional<Member> find(WorkspaceId workspaceId, UUID userId) {
            return stored.stream()
                    .filter(m -> m.workspaceId().equals(workspaceId) && m.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<Member> findByWorkspace(WorkspaceId workspaceId) {
            return stored.stream().filter(m -> m.workspaceId().equals(workspaceId)).toList();
        }

        @Override
        public List<Member> findByUser(UUID userId) {
            return stored.stream().filter(m -> m.userId().equals(userId)).toList();
        }

        @Override
        public long countByRole(WorkspaceId workspaceId, Role role) {
            return stored.stream().filter(m -> m.workspaceId().equals(workspaceId) && m.role() == role).count();
        }
    }

    static final class InMemoryWorkspaces implements WorkspaceRepository {

        final Map<WorkspaceId, Workspace> stored = new LinkedHashMap<>();
        private final InMemoryMembers members;

        InMemoryWorkspaces(InMemoryMembers members) {
            this.members = members;
        }

        @Override
        public void save(Workspace workspace) {
            stored.put(workspace.id(), workspace);
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public boolean existsBySlug(String slug) {
            return stored.values().stream().anyMatch(w -> w.slug().equals(slug));
        }

        @Override
        public List<Workspace> findByMember(UUID userId) {
            return members.findByUser(userId).stream()
                    .map(m -> stored.get(m.workspaceId()))
                    .sorted(Comparator.comparing(Workspace::createdAt))
                    .toList();
        }
    }

    static final class InMemoryInvitations implements InvitationRepository {

        final List<Invitation> stored = new ArrayList<>();

        @Override
        public void save(Invitation invitation) {
            if (!stored.contains(invitation)) {
                stored.add(invitation);
            }
        }

        @Override
        public Optional<Invitation> findById(InvitationId id) {
            return stored.stream().filter(i -> i.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Invitation> findPending(WorkspaceId workspaceId, Email email) {
            return stored.stream()
                    .filter(i -> i.isPending() && i.workspaceId().equals(workspaceId) && i.email().equals(email))
                    .findFirst();
        }

        @Override
        public List<Invitation> findPendingByWorkspace(WorkspaceId workspaceId) {
            return stored.stream().filter(i -> i.isPending() && i.workspaceId().equals(workspaceId)).toList();
        }

        @Override
        public List<Invitation> findPendingByEmail(Email email) {
            return stored.stream().filter(i -> i.isPending() && i.email().equals(email)).toList();
        }
    }

    static final class FakeUsers implements UserDirectory {

        final Map<UUID, UserView> byId = new HashMap<>();

        UserView add(String name, String email) {
            UserView user = new UserView(UUID.randomUUID(), email, name);
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

    static final class CapturingNotifier implements InvitationNotifier {

        final List<Notice> sent = new ArrayList<>();

        @Override
        public void invited(Notice notice) {
            sent.add(notice);
        }
    }
}
