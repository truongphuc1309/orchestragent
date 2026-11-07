package com.orchestragent.agent.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record AgentId(UUID value) implements Identifier {

    public AgentId {
        Objects.requireNonNull(value, "value");
    }

    public static AgentId generate() {
        return new AgentId(UuidV7.next());
    }
}
