package com.orchestragent.daemon.cli.runner;

import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.CommentInfo;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Writes the prompt of a task: who the agent is and its instructions, the issue with its comments, what this run
 * is for (the trigger), and how to report. The AI tool gets the whole story in every run, because a run may start
 * without the earlier session (another folder, a new computer).
 *
 * <p>The issue heading is {@code # Issue <key>: <title>} ({@code # Issue WEB-12: Add login}) on a line of its own; the mock provider reads it.
 */
public final class PromptBuilder {

    public String build(ClaimedTask task) {
        StringBuilder prompt = new StringBuilder();
        String agentName = orDefault(task.agent() == null ? null : task.agent().name(), "an agent");
        prompt.append("You are ").append(oneLine(agentName))
                .append(", an AI agent in Orchestragent, an issue tracker where people and AI agents work on the same issues.\n");

        String instructions = task.agent() == null ? null : task.agent().instructions();
        prompt.append("\n## Your instructions\n\n")
                .append(orDefault(instructions, "(No standing instructions. Use your best judgement.)").strip())
                .append('\n');

        prompt.append("\n# Issue ").append(oneLine(task.issue().label())).append(": ").append(oneLine(task.issue().title())).append("\n\n")
                .append(orDefault(task.issue().description(), "(No description.)").strip())
                .append('\n');

        List<CommentInfo> comments = task.comments() == null ? List.of() : task.comments();
        prompt.append("\n## Comments (oldest first)\n\n");
        if (comments.isEmpty()) {
            prompt.append("(No comments yet.)\n");
        }
        for (CommentInfo comment : comments) {
            prompt.append("### ").append(oneLine(orDefault(comment.authorName(), "Someone")))
                    .append(" (").append(describe(comment.authorType())).append(")");
            if (comment.createdAt() != null) {
                prompt.append(", ").append(DateTimeFormatter.ISO_INSTANT.format(comment.createdAt().truncatedTo(ChronoUnit.SECONDS)));
            }
            prompt.append("\n\n").append(orDefault(comment.body(), "").strip()).append("\n\n");
        }

        prompt.append("\n## What to do now\n\n").append(goal(task.trigger()));
        if (task.attempt() > 1) {
            prompt.append(" This is attempt ").append(task.attempt()).append('.');
        }
        prompt.append('\n');

        prompt.append("""

                ## How to report

                - Work only inside the current folder.
                - You cannot ask questions while you work. If something is unclear, make a reasonable choice and say so.
                - Your final message is posted as a comment on the issue, under your name. Make it a short summary \
                for the reviewer: what you did, which files you changed, and what is left or uncertain.
                - If you cannot do the work, say why in your final message and what you would need.
                - A person reviews your work and decides when the issue is done.
                """);
        return prompt.toString();
    }

    private static String goal(String trigger) {
        return switch (trigger == null ? "" : trigger) {
            case "comment" -> "Someone commented on the issue. Read the latest comments, answer them, and continue the"
                    + " work if they ask for it.";
            case "changes_requested" -> "A reviewer sent your work back. The latest comment says what to change. Make"
                    + " those changes.";
            case "retry" -> "An earlier run on this issue failed and someone asked to try again. The comments may say"
                    + " why it failed.";
            default -> "The issue was assigned to you. Do the work it describes.";
        };
    }

    private static String describe(String authorType) {
        return switch (authorType == null ? "" : authorType) {
            case "agent" -> "agent";
            case "system" -> "system";
            default -> "person";
        };
    }

    private static String orDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    /** Keeps a heading on one line, so a title cannot add headings of its own. */
    private static String oneLine(String text) {
        return orDefault(text, "").replaceAll("\\s+", " ").strip();
    }
}
