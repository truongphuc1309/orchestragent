package com.orchestragent.issue.domain;

import com.orchestragent.shared.id.Identifier;
import com.orchestragent.shared.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record IssueId(UUID value) implements Identifier {

    public IssueId {
        Objects.requireNonNull(value, "value");
    }

    public static IssueId generate() {
        return new IssueId(UuidV7.next());
    }
}
