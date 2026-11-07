package com.orchestragent.identity.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record ApiTokenId(UUID value) implements Identifier {

    public ApiTokenId {
        Objects.requireNonNull(value, "value");
    }

    public static ApiTokenId generate() {
        return new ApiTokenId(UuidV7.next());
    }
}
