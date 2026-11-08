package com.orchestragent.daemon.protocol;

import java.util.UUID;

/**
 * The issue a claimed task is about. {@code key} is its human-friendly name ({@code WEB-12}: project key and
 * {@code number}, which counts per project); a server older than projects sends no key, so it may be null.
 */
public record IssueInfo(UUID id, String key, int number, String title, String description) {

    /** {@code WEB-12}, or {@code #12} from a server that sends no key. */
    public String label() {
        return key == null || key.isBlank() ? "#" + number : key;
    }
}
