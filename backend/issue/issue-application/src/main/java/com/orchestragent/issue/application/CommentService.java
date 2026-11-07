package com.orchestragent.issue.application;

import com.orchestragent.issue.application.ActorNames.Lookup;
import com.orchestragent.issue.application.Views.CommentView;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments on issues. Members of the workspace read and write them; the check is the same as for issues. */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final IssueRepository issues;
    private final WorkspaceAccess access;
    private final ActorNames names;
    private final IssueEvents events;
    private final Clock clock;

    CommentService(
            CommentRepository comments,
            IssueRepository issues,
            WorkspaceAccess access,
            ActorNames names,
            IssueEvents events,
            Clock clock) {
        this.comments = comments;
        this.issues = issues;
        this.access = access;
        this.names = names;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CommentView add(UUID userId, UUID workspaceId, UUID issueId, String body) {
        access.requireMember(workspaceId, userId);
        IssueId id = requireIssue(workspaceId, issueId);

        Comment comment = Comment.write(workspaceId, id, ActorType.MEMBER, userId, body, clock.instant());
        comments.save(comment);
        events.commentAdded(comment);
        return toView(comment, names.resolve(workspaceId, List.of(Actor.member(userId))));
    }

    /** Oldest first. */
    @Transactional(readOnly = true)
    public List<CommentView> list(UUID userId, UUID workspaceId, UUID issueId) {
        access.requireMember(workspaceId, userId);
        IssueId id = requireIssue(workspaceId, issueId);

        List<Comment> found = comments.findByIssue(workspaceId, id);
        List<Actor> authors = new ArrayList<>();
        for (Comment comment : found) {
            if (comment.authorId() != null) {
                authors.add(new Actor(comment.authorType(), comment.authorId()));
            }
        }
        Lookup lookup = names.resolve(workspaceId, authors);
        return found.stream().map(comment -> toView(comment, lookup)).toList();
    }

    private IssueId requireIssue(UUID workspaceId, UUID issueId) {
        IssueId id = new IssueId(issueId);
        issues.find(workspaceId, id).orElseThrow(IssueService::notFound);
        return id;
    }

    private static CommentView toView(Comment comment, Lookup lookup) {
        return new CommentView(
                comment.id().value(),
                comment.issueId().value(),
                lookup.author(comment.authorType(), comment.authorId()),
                comment.body(),
                comment.createdAt());
    }
}
