package com.orchestragent.identity.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) implements Identifier {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId generate() {
        return new UserId(UuidV7.next());
    }
}
