package com.orchestragent.identity.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record LoginCodeId(UUID value) implements Identifier {

    public LoginCodeId {
        Objects.requireNonNull(value, "value");
    }

    public static LoginCodeId generate() {
        return new LoginCodeId(UuidV7.next());
    }
}
