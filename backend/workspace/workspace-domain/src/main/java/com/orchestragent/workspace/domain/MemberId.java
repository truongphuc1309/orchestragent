package com.orchestragent.workspace.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record MemberId(UUID value) implements Identifier {

    public MemberId {
        Objects.requireNonNull(value, "value");
    }

    public static MemberId generate() {
        return new MemberId(UuidV7.next());
    }
}
