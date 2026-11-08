package com.orchestragent.issue.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The human-friendly name of an issue: its project key and its number in that project, written {@code WEB-12}. */
public record IssueKey(ProjectKey project, int number) {

    private static final Pattern FORMAT = Pattern.compile("([A-Z][A-Z0-9]*)-([1-9][0-9]{0,8})");

    public IssueKey {
        Objects.requireNonNull(project, "project");
        if (number < 1) {
            throw new IllegalArgumentException("Issue numbers start at 1");
        }
    }

    /** Reads {@code WEB-12}; anything else (lower case, no number, spaces) is empty. */
    public static Optional<IssueKey> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(text);
        if (!matcher.matches() || !ProjectKey.isValid(matcher.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new IssueKey(new ProjectKey(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }

    @Override
    public String toString() {
        return project.value() + "-" + number;
    }
}
