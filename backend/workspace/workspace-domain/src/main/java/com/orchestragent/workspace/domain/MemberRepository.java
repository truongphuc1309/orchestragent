package com.orchestragent.workspace.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {

    void save(Member member);

    void delete(Member member);

    Optional<Member> find(WorkspaceId workspaceId, UUID userId);

    /** Members of a workspace, oldest first. */
    List<Member> findByWorkspace(WorkspaceId workspaceId);

    List<Member> findByUser(UUID userId);

    long countByRole(WorkspaceId workspaceId, Role role);
}
