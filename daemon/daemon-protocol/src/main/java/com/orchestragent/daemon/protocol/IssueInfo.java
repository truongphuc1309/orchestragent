package com.orchestragent.daemon.protocol;

import java.util.UUID;

public record IssueInfo(UUID id, int number, String title, String description) {
}
