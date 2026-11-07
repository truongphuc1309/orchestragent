package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.identity.domain.LoginCodeId;
import com.orchestragent.identity.domain.LoginCodeRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LoginCodeRepositoryAdapter implements LoginCodeRepository {

    private final LoginCodeJpaRepository jpa;

    LoginCodeRepositoryAdapter(LoginCodeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(LoginCode code) {
        jpa.findById(code.id().value()).ifPresentOrElse(
                existing -> existing.updateFrom(code.attempts(), code.usedAt()),
                () -> jpa.save(new LoginCodeEntity(
                        code.id().value(),
                        code.email().value(),
                        code.codeHash(),
                        code.expiresAt(),
                        code.attempts(),
                        code.usedAt(),
                        code.createdAt())));
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(Email email) {
        return jpa.findFirstByEmailOrderByCreatedAtDesc(email.value()).map(LoginCodeEntity::getCreatedAt);
    }

    @Override
    public Optional<LoginCode> findLatestForUpdate(Email email) {
        return jpa.findLatestForUpdate(email.value()).map(entity -> new LoginCode(
                new LoginCodeId(entity.getId()),
                Email.of(entity.getEmail()),
                entity.getCodeHash(),
                entity.getExpiresAt(),
                entity.getAttempts(),
                entity.getUsedAt(),
                entity.getCreatedAt()));
    }
}
