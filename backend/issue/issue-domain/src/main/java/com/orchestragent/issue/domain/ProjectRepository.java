package com.orchestragent.issue.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    /** Inserts a new project or updates the editable fields of an existing one. */
    void save(Project project);

    Optional<Project> find(UUID workspaceId, ProjectId id);

    Optional<Project> findByKey(UUID workspaceId, ProjectKey key);

    /** Whether any project of the workspace, archived or not, has this key. */
    boolean keyExists(UUID workspaceId, ProjectKey key);

    /** Ordered by key. Archived projects only when {@code includeArchived}. */
    List<Project> list(UUID workspaceId, boolean includeArchived);
}
