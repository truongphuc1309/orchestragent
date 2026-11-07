package com.orchestragent.agent.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record RuntimeId(UUID value) implements Identifier {

    public RuntimeId {
        Objects.requireNonNull(value, "value");
    }

    public static RuntimeId generate() {
        return new RuntimeId(UuidV7.next());
    }
}
