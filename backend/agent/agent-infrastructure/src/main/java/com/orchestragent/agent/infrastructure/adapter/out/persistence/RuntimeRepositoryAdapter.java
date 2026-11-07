package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.agent.domain.RuntimeStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeRepositoryAdapter implements RuntimeRepository {

    private final RuntimeJpaRepository jpa;

    RuntimeRepositoryAdapter(RuntimeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Runtime runtime) {
        jpa.findById(runtime.id().value()).ifPresentOrElse(
                existing -> existing.update(
                        runtime.deviceName(),
                        runtime.providerVersion(),
                        runtime.status().code(),
                        runtime.lastHeartbeatAt()),
                () -> jpa.save(new RuntimeEntity(
                        runtime.id().value(),
                        runtime.workspaceId(),
                        runtime.ownerUserId(),
                        runtime.daemonId(),
                        runtime.deviceName(),
                        runtime.provider(),
                        runtime.providerVersion(),
                        runtime.status().code(),
                        runtime.lastHeartbeatAt())));
    }

    @Override
    public Optional<Runtime> find(UUID workspaceId, RuntimeId id) {
        return jpa.findByIdAndWorkspaceId(id.value(), workspaceId).map(RuntimeRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Runtime> findByDaemon(UUID workspaceId, String daemonId, String provider) {
        return jpa.findByWorkspaceIdAndDaemonIdAndProvider(workspaceId, daemonId, provider)
                .map(RuntimeRepositoryAdapter::toDomain);
    }

    @Override
    public List<Runtime> findByWorkspace(UUID workspaceId) {
        return jpa.findByWorkspaceIdOrderByIdAsc(workspaceId).stream().map(RuntimeRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Runtime> findOwnedBy(UUID ownerUserId, Collection<RuntimeId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByOwnerUserIdAndIdIn(ownerUserId, ids.stream().map(RuntimeId::value).toList()).stream()
                .map(RuntimeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Runtime> findOnlineSilentSince(Instant cutoff) {
        return jpa.findByStatusAndLastHeartbeatAtBefore(RuntimeStatus.ONLINE.code(), cutoff).stream()
                .map(RuntimeRepositoryAdapter::toDomain)
                .toList();
    }

    private static Runtime toDomain(RuntimeEntity entity) {
        return new Runtime(
                new RuntimeId(entity.getId()),
                entity.getWorkspaceId(),
                entity.getOwnerUserId(),
                entity.getDaemonId(),
                entity.getDeviceName(),
                entity.getProvider(),
                entity.getProviderVersion(),
                RuntimeStatus.fromCode(entity.getStatus()),
                entity.getLastHeartbeatAt());
    }
}
