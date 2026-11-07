package com.orchestragent.issue.application;

import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.issue.domain.Positions;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The review gate: when an agent's work lands in "in review", a person decides. They approve it (the issue is
 * done) or send it back with a comment saying what to change (the issue returns to "to do", the comment is
 * added, and the agent is asked to rework it). An agent can never mark its own work as done.
 *
 * <p>Any member of the workspace may review. Both calls need the issue to be in review, so a stale screen
 * (someone else already decided) gets a conflict, not a second decision.
 */
@Service
public class ReviewService {

    private final IssueRepository issues;
    private final CommentRepository comments;
    private final WorkspaceAccess access;
    private final IssueService issueService;
    private final IssueEvents events;
    private final Clock clock;

    ReviewService(
            IssueRepository issues,
            CommentRepository comments,
            WorkspaceAccess access,
            IssueService issueService,
            IssueEvents events,
            Clock clock) {
        this.issues = issues;
        this.comments = comments;
        this.access = access;
        this.issueService = issueService;
        this.events = events;
        this.clock = clock;
    }

    /** In review → done. */
    @Transactional
    public IssueView approve(UUID userId, UUID workspaceId, UUID issueId) {
        access.requireMember(workspaceId, userId);
        Issue issue = inReview(workspaceId, issueId);

        move(issue, Status.DONE);
        issues.save(issue);
        events.updated(issue, Set.of("status", "position"), userId);
        return issueService.get(userId, workspaceId, issueId);
    }

    /**
     * In review → to do, with the reviewer's comment (which the agent reads when it works on the issue again).
     * The comment is required: sending work back without saying why helps nobody.
     */
    @Transactional
    public IssueView requestChanges(UUID userId, UUID workspaceId, UUID issueId, String commentText) {
        access.requireMember(workspaceId, userId);
        Issue issue = inReview(workspaceId, issueId);
        Comment comment = Comment.write(
                workspaceId, issue.id(), ActorType.MEMBER, userId, commentText, clock.instant());

        comments.save(comment);
        move(issue, Status.TODO);
        issues.save(issue);
        events.updated(issue, Set.of("status", "position"), userId);
        events.commentAdded(comment, true);
        return issueService.get(userId, workspaceId, issueId);
    }

    private Issue inReview(UUID workspaceId, UUID issueId) {
        Issue issue = issues.find(workspaceId, new IssueId(issueId)).orElseThrow(IssueService::notFound);
        if (issue.status() != Status.IN_REVIEW) {
            throw new ConflictException("issue.not_in_review", "Only an issue that is in review can be approved or sent back");
        }
        return issue;
    }

    private void move(Issue issue, Status status) {
        Instant now = clock.instant();
        var max = issues.maxPosition(issue.workspaceId(), status);
        issue.moveTo(status, Positions.append(max.isPresent() ? max.getAsDouble() : null), now);
    }
}
