package com.orchestragent.identity.application;

import com.orchestragent.identity.contract.ApiTokenVerifier;
import com.orchestragent.identity.contract.VerifiedApiToken;
import com.orchestragent.identity.domain.ApiTokenRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ApiTokenVerifierService implements ApiTokenVerifier {

    private final ApiTokenRepository tokens;
    private final Clock clock;

    ApiTokenVerifierService(ApiTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<VerifiedApiToken> verify(String rawToken) {
        if (!ApiTokens.looksLikeApiToken(rawToken)) {
            return Optional.empty();
        }
        return tokens.findByHash(ApiTokens.hash(rawToken))
                .filter(token -> !token.isRevoked())
                .map(token -> {
                    if (token.recordUse(clock.instant())) {
                        tokens.save(token);
                    }
                    return new VerifiedApiToken(token.id().value(), token.userId().value());
                });
    }
}
