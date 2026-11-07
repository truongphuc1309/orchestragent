package com.orchestragent.daemon.protocol;

import java.time.Instant;

/** A comment on the issue, oldest first. {@code authorType} is member, agent or system. */
public record CommentInfo(String authorType, String authorName, String body, Instant createdAt) {
}
