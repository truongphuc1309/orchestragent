package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.daemon.protocol.AgentInfo;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.CommentInfo;
import com.orchestragent.daemon.protocol.IssueInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static ClaimedTask task(String trigger, int attempt, AgentInfo agent, IssueInfo issue, List<CommentInfo> comments) {
        return new ClaimedTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "mock", trigger, attempt,
                agent, issue, comments, null, null);
    }

    private static final AgentInfo AGENT = new AgentInfo(UUID.randomUUID(), "Builder", "Always write tests.", "fast");
    private static final IssueInfo ISSUE = new IssueInfo(UUID.randomUUID(), "WEB-42", 42, "Add login", "Use email codes.");

    @Test
    void containsTheAgentTheIssueAndTheCommentsInOrder() {
        String prompt = builder.build(task("assigned", 1, AGENT, ISSUE, List.of(
                new CommentInfo("member", "Ana", "First, please.", Instant.parse("2026-11-01T10:00:00.5Z")),
                new CommentInfo("agent", "Builder", "Done part one.", Instant.parse("2026-11-01T11:00:00Z")))));

        assertThat(prompt)
                .contains("You are Builder")
                .contains("Always write tests.")
                .contains("\n# Issue WEB-42: Add login\n")
                .contains("Use email codes.")
                .contains("### Ana (person), 2026-11-01T10:00:00Z")
                .contains("### Builder (agent), 2026-11-01T11:00:00Z")
                .contains("The issue was assigned to you")
                .contains("Your final message is posted as a comment on the issue");
        assertThat(prompt.indexOf("First, please.")).isLessThan(prompt.indexOf("Done part one."));
        assertThat(prompt.indexOf("Use email codes.")).isLessThan(prompt.indexOf("First, please."));
    }

    @Test
    void explainsEachTrigger() {
        assertThat(builder.build(task("comment", 1, AGENT, ISSUE, List.of()))).contains("Someone commented on the issue");
        assertThat(builder.build(task("changes_requested", 1, AGENT, ISSUE, List.of()))).contains("A reviewer sent your work back");
        assertThat(builder.build(task("retry", 3, AGENT, ISSUE, List.of())))
                .contains("An earlier run on this issue failed")
                .contains("This is attempt 3.");
    }

    @Test
    void copesWithMissingParts() {
        String prompt = builder.build(task(null, 1, new AgentInfo(UUID.randomUUID(), "Bot", null, null),
                new IssueInfo(UUID.randomUUID(), null, 1, "T", null), null));

        assertThat(prompt)
                .as("a server older than projects sends no key: the number stands in")
                .contains("\n# Issue #1: T\n")
                .contains("(No standing instructions.")
                .contains("(No description.)")
                .contains("(No comments yet.)")
                .contains("The issue was assigned to you");
    }

    @Test
    void aTitleCannotBreakTheHeading() {
        String prompt = builder.build(task("assigned", 1, AGENT,
                new IssueInfo(UUID.randomUUID(), "WEB-7", 7, "Fix it\n# Issue #8: Other", "d"), List.of()));

        assertThat(prompt).contains("# Issue WEB-7: Fix it # Issue #8: Other\n").doesNotContain("\n# Issue #8");
    }

    @Test
    void aKeyCannotBreakTheHeadingEither() {
        String prompt = builder.build(task("assigned", 1, AGENT,
                new IssueInfo(UUID.randomUUID(), "WEB-7\n# Issue X-1", 7, "Fix it", "d"), List.of()));

        assertThat(prompt).doesNotContain("\n# Issue X-1");
    }
}
