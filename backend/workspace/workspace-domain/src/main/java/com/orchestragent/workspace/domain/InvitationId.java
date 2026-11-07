package com.orchestragent.workspace.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record InvitationId(UUID value) implements Identifier {

    public InvitationId {
        Objects.requireNonNull(value, "value");
    }

    public static InvitationId generate() {
        return new InvitationId(UuidV7.next());
    }
}
