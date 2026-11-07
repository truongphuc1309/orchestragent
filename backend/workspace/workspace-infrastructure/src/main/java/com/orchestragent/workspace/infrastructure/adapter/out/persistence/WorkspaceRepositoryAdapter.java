package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import com.orchestragent.workspace.domain.Workspace;
import com.orchestragent.workspace.domain.WorkspaceId;
import com.orchestragent.workspace.domain.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private final WorkspaceJpaRepository jpa;

    WorkspaceRepositoryAdapter(WorkspaceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Workspace workspace) {
        // Workspaces are immutable once created, so saving is always an insert.
        jpa.save(new WorkspaceEntity(
                workspace.id().value(), workspace.name(), workspace.slug(), workspace.createdBy(), workspace.createdAt()));
    }

    @Override
    public Optional<Workspace> findById(WorkspaceId id) {
        return jpa.findById(id.value()).map(WorkspaceRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public List<Workspace> findByMember(UUID userId) {
        return jpa.findByMember(userId).stream().map(WorkspaceRepositoryAdapter::toDomain).toList();
    }

    private static Workspace toDomain(WorkspaceEntity entity) {
        return new Workspace(
                new WorkspaceId(entity.getId()),
                entity.getName(),
                entity.getSlug(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
