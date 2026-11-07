package com.orchestragent.issue.application;

import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.issue.domain.Positions;
import com.orchestragent.issue.domain.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the issue does when an agent's work changes state:
 *
 * <ul>
 *   <li>a task starts: the issue moves to "in progress";
 *   <li>a task completes: the agent's result becomes a comment of the agent and the issue moves to "in review",
 *       where a person decides (see {@link ReviewService});
 *   <li>a task fails: a system comment says why and the issue moves to "blocked".
 * </ul>
 *
 * The agent context announces these events at least once, so each method is safe to run twice: a comment
 * remembers the event it came from, and moving to the status the issue is already in does nothing.
 */
@Service
public class AgentRunReactions {

    /** Said when the agent finished without writing anything: the comment must still exist, as the review starts from it. */
    static final String NO_RESULT = "The agent finished without a written result.";

    private static final String CUT = "\n\n… (cut: too long)";

    private final IssueRepository issues;
    private final CommentRepository comments;
    private final IssueEvents events;
    private final Clock clock;

    AgentRunReactions(IssueRepository issues, CommentRepository comments, IssueEvents events, Clock clock) {
        this.issues = issues;
        this.comments = comments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void taskStarted(UUID workspaceId, UUID issueId, UUID agentId) {
        find(workspaceId, issueId).ifPresent(issue -> moveTo(issue, Status.IN_PROGRESS, agentId));
    }

    @Transactional
    public void taskCompleted(UUID eventId, UUID workspaceId, UUID issueId, UUID agentId, String output) {
        find(workspaceId, issueId).ifPresent(issue -> {
            writeOnce(eventId, issue, ActorType.AGENT, agentId, resultComment(output));
            moveTo(issue, Status.IN_REVIEW, agentId);
        });
    }

    @Transactional
    public void taskFailed(
            UUID eventId, UUID workspaceId, UUID issueId, UUID agentId, String failureReason, String error) {
        find(workspaceId, issueId).ifPresent(issue -> {
            writeOnce(eventId, issue, ActorType.SYSTEM, null, failureComment(failureReason, error));
            moveTo(issue, Status.BLOCKED, agentId);
        });
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private Optional<Issue> find(UUID workspaceId, UUID issueId) {
        // The issue may have been deleted while the agent was working: then there is nothing to update.
        return issues.find(workspaceId, new IssueId(issueId));
    }

    private void writeOnce(UUID eventId, Issue issue, ActorType type, UUID authorId, String body) {
        if (comments.existsBySourceEvent(eventId)) {
            return;
        }
        Comment comment = Comment.writeFor(
                eventId,
                issue.workspaceId(),
                issue.id(),
                com.orchestragent.issue.domain.ActorType.valueOf(type.name()),
                authorId,
                body,
                clock.instant());
        comments.save(comment);
        events.commentAdded(comment);
    }

    /** Puts the issue at the bottom of the column of the new status. Does nothing when it is already there. */
    private void moveTo(Issue issue, Status status, UUID agentId) {
        if (issue.status() == status) {
            return;
        }
        Instant now = clock.instant();
        OptionalDouble max = issues.maxPosition(issue.workspaceId(), status);
        issue.moveTo(status, Positions.append(max.isPresent() ? max.getAsDouble() : null), now);
        issues.save(issue);
        events.updated(issue, Set.of("status", "position"), ActorType.AGENT, agentId);
    }

    static String resultComment(String output) {
        String text = output == null ? "" : output.strip();
        if (text.isEmpty()) {
            return NO_RESULT;
        }
        return text.length() <= Comment.MAX_BODY_LENGTH
                ? text
                : text.substring(0, Comment.MAX_BODY_LENGTH - CUT.length()) + CUT;
    }

    static String failureComment(String failureReason, String error) {
        String reason =
                switch (failureReason == null ? "" : failureReason) {
                    case "runtime_offline" -> "its computer went offline";
                    case "timeout" -> "it ran out of time";
                    case "agent_error", "" -> "the agent reported an error";
                    default -> failureReason.replace('_', ' ').toLowerCase(Locale.ROOT);
                };
        String text = "The agent could not finish this task: " + reason + ".";
        String detail = error == null ? "" : error.strip();
        if (detail.isEmpty()) {
            return text;
        }
        String combined = text + "\n\n" + detail;
        return combined.length() <= Comment.MAX_BODY_LENGTH
                ? combined
                : combined.substring(0, Comment.MAX_BODY_LENGTH - CUT.length()) + CUT;
    }
}
