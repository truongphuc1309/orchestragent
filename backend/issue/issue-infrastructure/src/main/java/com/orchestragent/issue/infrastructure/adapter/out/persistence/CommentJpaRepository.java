package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommentJpaRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findByWorkspaceIdAndIssueIdOrderByCreatedAtAscIdAsc(UUID workspaceId, UUID issueId);
}
