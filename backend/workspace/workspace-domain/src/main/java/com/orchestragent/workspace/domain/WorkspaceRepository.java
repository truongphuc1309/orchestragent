package com.orchestragent.workspace.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    void save(Workspace workspace);

    Optional<Workspace> findById(WorkspaceId id);

    boolean existsBySlug(String slug);

    /** Workspaces the user is a member of, oldest first. */
    List<Workspace> findByMember(UUID userId);
}
