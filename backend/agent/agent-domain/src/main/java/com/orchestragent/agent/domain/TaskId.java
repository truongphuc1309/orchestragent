package com.orchestragent.agent.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record TaskId(UUID value) implements Identifier {

    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskId generate() {
        return new TaskId(UuidV7.next());
    }
}
