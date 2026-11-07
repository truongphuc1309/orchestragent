package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, UUID> {

    boolean existsBySlug(String slug);

    @Query("""
            select w from WorkspaceEntity w
            where w.id in (select m.workspaceId from MemberEntity m where m.userId = :userId)
            order by w.createdAt asc, w.id asc
            """)
    List<WorkspaceEntity> findByMember(@Param("userId") UUID userId);
}
