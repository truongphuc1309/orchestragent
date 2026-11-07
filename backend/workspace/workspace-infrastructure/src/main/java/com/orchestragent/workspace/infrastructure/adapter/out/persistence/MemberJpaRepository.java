package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaRepository extends JpaRepository<MemberEntity, UUID> {

    Optional<MemberEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<MemberEntity> findByWorkspaceIdOrderByCreatedAtAscIdAsc(UUID workspaceId);

    List<MemberEntity> findByUserId(UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, String role);
}
