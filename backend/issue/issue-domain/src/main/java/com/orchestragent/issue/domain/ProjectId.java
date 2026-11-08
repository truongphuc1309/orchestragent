package com.orchestragent.issue.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record ProjectId(UUID value) implements Identifier {

    public ProjectId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectId generate() {
        return new ProjectId(UuidV7.next());
    }
}
