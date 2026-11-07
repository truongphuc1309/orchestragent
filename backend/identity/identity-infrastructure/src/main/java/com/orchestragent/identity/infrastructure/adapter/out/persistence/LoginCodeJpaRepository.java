package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoginCodeJpaRepository extends JpaRepository<LoginCodeEntity, UUID> {

    Optional<LoginCodeEntity> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoginCodeEntity c where c.email = :email order by c.createdAt desc limit 1")
    Optional<LoginCodeEntity> findLatestForUpdate(@Param("email") String email);
}
