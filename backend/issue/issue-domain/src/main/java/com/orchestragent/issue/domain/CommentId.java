package com.orchestragent.issue.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record CommentId(UUID value) implements Identifier {

    public CommentId {
        Objects.requireNonNull(value, "value");
    }

    public static CommentId generate() {
        return new CommentId(UuidV7.next());
    }
}
