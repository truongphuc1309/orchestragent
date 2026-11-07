package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, String status);

    List<InvitationEntity> findByWorkspaceIdAndStatusOrderByCreatedAtDescIdDesc(UUID workspaceId, String status);

    List<InvitationEntity> findByEmailAndStatusOrderByCreatedAtDescIdDesc(String email, String status);
}
