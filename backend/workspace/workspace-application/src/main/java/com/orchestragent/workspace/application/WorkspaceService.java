package com.orchestragent.workspace.application;

import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.Slug;
import com.orchestragent.workspace.domain.Workspace;
import com.orchestragent.workspace.domain.WorkspaceId;
import com.orchestragent.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    /** After this many taken slugs a random suffix is used, so the loop always ends. */
    private static final int MAX_SEQUENTIAL_SLUGS = 50;

    private final WorkspaceRepository workspaces;
    private final MemberRepository members;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, MemberRepository members, Clock clock) {
        this.workspaces = workspaces;
        this.members = members;
        this.clock = clock;
    }

    /** Creates the workspace and makes the creator its owner. */
    @Transactional
    public WorkspaceView create(UUID userId, String name) {
        Instant now = clock.instant();
        String cleanName = Workspace.validName(name);
        Workspace workspace = Workspace.create(cleanName, uniqueSlug(Slug.fromName(cleanName)), userId, now);
        workspaces.save(workspace);
        members.save(Member.join(workspace.id(), userId, Role.OWNER, now));
        return toView(workspace, Role.OWNER);
    }

    /** Workspaces of the user, oldest first. */
    @Transactional(readOnly = true)
    public List<WorkspaceView> listMine(UUID userId) {
        Map<WorkspaceId, Role> roles =
                members.findByUser(userId).stream().collect(Collectors.toMap(Member::workspaceId, Member::role));
        return workspaces.findByMember(userId).stream()
                .map(workspace -> toView(workspace, roles.get(workspace.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceView get(UUID userId, UUID workspaceId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        Member member = members.find(id, userId).orElseThrow(WorkspaceService::notFound);
        Workspace workspace = workspaces.findById(id).orElseThrow(WorkspaceService::notFound);
        return toView(workspace, member.role());
    }

    private String uniqueSlug(String base) {
        for (int attempt = 1; attempt <= MAX_SEQUENTIAL_SLUGS; attempt++) {
            String candidate = Slug.candidate(base, attempt);
            if (!workspaces.existsBySlug(candidate)) {
                return candidate;
            }
        }
        byte[] random = new byte[3];
        ThreadLocalRandom.current().nextBytes(random);
        return base + "-" + HexFormat.of().formatHex(random);
    }

    static WorkspaceView toView(Workspace workspace, Role role) {
        return new WorkspaceView(
                workspace.id().value(), workspace.name(), workspace.slug(), Roles.toContract(role), workspace.createdAt());
    }

    static NotFoundException notFound() {
        return new NotFoundException("workspace.not_found", "Workspace not found");
    }
}
