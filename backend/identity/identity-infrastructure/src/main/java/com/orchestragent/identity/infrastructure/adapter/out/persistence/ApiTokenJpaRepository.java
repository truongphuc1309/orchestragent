package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApiTokenJpaRepository extends JpaRepository<ApiTokenEntity, UUID> {

    Optional<ApiTokenEntity> findByTokenHash(String tokenHash);

    List<ApiTokenEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
