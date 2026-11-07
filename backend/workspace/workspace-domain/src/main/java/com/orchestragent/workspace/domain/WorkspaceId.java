package com.orchestragent.workspace.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceId(UUID value) implements Identifier {

    public WorkspaceId {
        Objects.requireNonNull(value, "value");
    }

    public static WorkspaceId generate() {
        return new WorkspaceId(UuidV7.next());
    }
}
