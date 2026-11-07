package com.orchestragent.identity.domain;

import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository {

    void save(ApiToken token);

    Optional<ApiToken> findById(ApiTokenId id);

    Optional<ApiToken> findByHash(String tokenHash);

    /** Newest first, revoked tokens included. */
    List<ApiToken> findByUser(UserId userId);
}
