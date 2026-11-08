package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import com.orchestragent.issue.domain.Project;
import com.orchestragent.issue.domain.ProjectId;
import com.orchestragent.issue.domain.ProjectKey;
import com.orchestragent.issue.domain.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpa;

    ProjectRepositoryAdapter(ProjectJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Project project) {
        jpa.findById(project.id().value()).ifPresentOrElse(
                existing -> existing.update(
                        project.name(), project.description(), project.archivedAt(), project.updatedAt()),
                () -> jpa.save(new ProjectEntity(
                        project.id().value(),
                        project.workspaceId(),
                        project.key().value(),
                        project.name(),
                        project.description(),
                        project.createdBy(),
                        project.archivedAt(),
                        project.createdAt(),
                        project.updatedAt())));
    }

    @Override
    public Optional<Project> find(UUID workspaceId, ProjectId id) {
        return jpa.findByIdAndWorkspaceId(id.value(), workspaceId).map(ProjectRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Project> findByKey(UUID workspaceId, ProjectKey key) {
        return jpa.findByWorkspaceIdAndProjectKey(workspaceId, key.value()).map(ProjectRepositoryAdapter::toDomain);
    }

    @Override
    public boolean keyExists(UUID workspaceId, ProjectKey key) {
        return jpa.existsByWorkspaceIdAndProjectKey(workspaceId, key.value());
    }

    @Override
    public List<Project> list(UUID workspaceId, boolean includeArchived) {
        List<ProjectEntity> found = includeArchived
                ? jpa.findByWorkspaceIdOrderByProjectKeyAsc(workspaceId)
                : jpa.findByWorkspaceIdAndArchivedAtIsNullOrderByProjectKeyAsc(workspaceId);
        return found.stream().map(ProjectRepositoryAdapter::toDomain).toList();
    }

    private static Project toDomain(ProjectEntity entity) {
        return new Project(
                new ProjectId(entity.getId()),
                entity.getWorkspaceId(),
                new ProjectKey(entity.getProjectKey()),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedBy(),
                entity.getArchivedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
