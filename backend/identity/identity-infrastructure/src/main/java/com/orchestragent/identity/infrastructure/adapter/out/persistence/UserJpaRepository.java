package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    /** Plain SQL so a concurrent insert of the same email is a no-op instead of an aborted transaction. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO identity.users (id, email, name, created_at)
            VALUES (:id, :email, :name, :createdAt)
            ON CONFLICT (email) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("email") String email,
            @Param("name") String name,
            @Param("createdAt") Instant createdAt);
}
