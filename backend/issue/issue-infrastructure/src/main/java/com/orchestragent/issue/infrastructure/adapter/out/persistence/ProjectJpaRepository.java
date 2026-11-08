package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<ProjectEntity> findByWorkspaceIdAndProjectKey(UUID workspaceId, String projectKey);

    boolean existsByWorkspaceIdAndProjectKey(UUID workspaceId, String projectKey);

    List<ProjectEntity> findByWorkspaceIdOrderByProjectKeyAsc(UUID workspaceId);

    List<ProjectEntity> findByWorkspaceIdAndArchivedAtIsNullOrderByProjectKeyAsc(UUID workspaceId);
}
