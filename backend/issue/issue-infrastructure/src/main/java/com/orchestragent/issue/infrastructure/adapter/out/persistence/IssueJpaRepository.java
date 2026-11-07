package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IssueJpaRepository extends JpaRepository<IssueEntity, UUID>, JpaSpecificationExecutor<IssueEntity> {

    Optional<IssueEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<IssueEntity> findByWorkspaceIdAndNumber(UUID workspaceId, int number);

    List<IssueEntity> findByWorkspaceIdAndStatusOrderByPositionAscNumberAsc(UUID workspaceId, String status);

    @Query("select max(i.position) from IssueEntity i where i.workspaceId = :workspaceId and i.status = :status")
    Optional<Double> maxPosition(@Param("workspaceId") UUID workspaceId, @Param("status") String status);
}
