package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RuntimeJpaRepository extends JpaRepository<RuntimeEntity, UUID> {

    Optional<RuntimeEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<RuntimeEntity> findByWorkspaceIdAndDaemonIdAndProvider(UUID workspaceId, String daemonId, String provider);

    /** UUIDv7 IDs sort by creation time, so this is "oldest registration first". */
    List<RuntimeEntity> findByWorkspaceIdOrderByIdAsc(UUID workspaceId);

    List<RuntimeEntity> findByOwnerUserIdAndIdIn(UUID ownerUserId, Collection<UUID> ids);

    List<RuntimeEntity> findByStatusAndLastHeartbeatAtBefore(String status, Instant cutoff);
}
