package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import com.orchestragent.identity.domain.ApiToken;
import com.orchestragent.identity.domain.ApiTokenId;
import com.orchestragent.identity.domain.ApiTokenRepository;
import com.orchestragent.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ApiTokenRepositoryAdapter implements ApiTokenRepository {

    private final ApiTokenJpaRepository jpa;

    ApiTokenRepositoryAdapter(ApiTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(ApiToken token) {
        jpa.findById(token.id().value()).ifPresentOrElse(
                existing -> existing.updateFrom(token.lastUsedAt(), token.revokedAt()),
                () -> jpa.save(new ApiTokenEntity(
                        token.id().value(),
                        token.userId().value(),
                        token.name(),
                        token.tokenPrefix(),
                        token.tokenHash(),
                        token.lastUsedAt(),
                        token.revokedAt(),
                        token.createdAt())));
    }

    @Override
    public Optional<ApiToken> findById(ApiTokenId id) {
        return jpa.findById(id.value()).map(ApiTokenRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<ApiToken> findByHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(ApiTokenRepositoryAdapter::toDomain);
    }

    @Override
    public List<ApiToken> findByUser(UserId userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId.value()).stream()
                .map(ApiTokenRepositoryAdapter::toDomain)
                .toList();
    }

    private static ApiToken toDomain(ApiTokenEntity entity) {
        return new ApiToken(
                new ApiTokenId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getName(),
                entity.getTokenPrefix(),
                entity.getTokenHash(),
                entity.getLastUsedAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt());
    }
}
