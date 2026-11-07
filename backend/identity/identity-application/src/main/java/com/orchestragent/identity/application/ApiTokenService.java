package com.orchestragent.identity.application;

import com.orchestragent.identity.domain.ApiToken;
import com.orchestragent.identity.domain.ApiTokenId;
import com.orchestragent.identity.domain.ApiTokenRepository;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Create, list and revoke the API tokens of the calling user. */
@Service
public class ApiTokenService {

    public static final int MAX_NAME_LENGTH = 100;

    private final ApiTokenRepository tokens;
    private final Clock clock;

    public ApiTokenService(ApiTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** @return the token with its raw value; the raw value is never retrievable again */
    @Transactional
    public CreatedApiToken create(UUID userId, String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "api_token.invalid_name", "Token name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        String raw = ApiTokens.generate();
        ApiToken token = ApiToken.create(
                new UserId(userId), cleanName, ApiTokens.displayPrefix(raw), ApiTokens.hash(raw), clock.instant());
        tokens.save(token);
        return new CreatedApiToken(toInfo(token), raw);
    }

    @Transactional(readOnly = true)
    public List<ApiTokenInfo> list(UUID userId) {
        return tokens.findByUser(new UserId(userId)).stream().map(ApiTokenService::toInfo).toList();
    }

    /** Someone else's token looks exactly like a missing one, so IDs cannot be probed. */
    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        ApiToken token = tokens.findById(new ApiTokenId(tokenId))
                .filter(candidate -> candidate.isOwnedBy(new UserId(userId)))
                .orElseThrow(() -> new NotFoundException("api_token.not_found", "API token not found"));
        token.revoke(clock.instant());
        tokens.save(token);
    }

    private static ApiTokenInfo toInfo(ApiToken token) {
        return new ApiTokenInfo(
                token.id().value(),
                token.name(),
                token.tokenPrefix(),
                token.lastUsedAt(),
                token.revokedAt(),
                token.createdAt());
    }

    public record ApiTokenInfo(
            UUID id, String name, String tokenPrefix, Instant lastUsedAt, Instant revokedAt, Instant createdAt) {
    }

    public record CreatedApiToken(ApiTokenInfo info, String rawToken) {
    }
}
