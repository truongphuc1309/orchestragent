package com.orchestragent.issue.domain;

import java.util.Objects;
import java.util.UUID;

/** Who is doing or owning something: a member (user ID) or an agent (agent ID). */
public record Actor(ActorType type, UUID id) {

    public Actor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (type == ActorType.SYSTEM) {
            throw new IllegalArgumentException("The system has no ID; use a null actor instead");
        }
    }

    public static Actor member(UUID userId) {
        return new Actor(ActorType.MEMBER, userId);
    }

    public static Actor agent(UUID agentId) {
        return new Actor(ActorType.AGENT, agentId);
    }
}
