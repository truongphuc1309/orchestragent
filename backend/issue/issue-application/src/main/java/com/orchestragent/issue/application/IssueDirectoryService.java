package com.orchestragent.issue.application;

import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentDetails;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The issue context's answer to other contexts. It never leaks an issue of another workspace. */
@Service
class IssueDirectoryService implements IssueDirectory {

    private final IssueRepository issues;
    private final CommentRepository comments;
    private final ActorNames names;

    IssueDirectoryService(IssueRepository issues, CommentRepository comments, ActorNames names) {
        this.issues = issues;
        this.comments = comments;
        this.names = names;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IssueDetails> find(UUID workspaceId, UUID issueId) {
        return issues.find(workspaceId, new IssueId(issueId)).map(IssueDirectoryService::details);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDetails> listComments(UUID workspaceId, UUID issueId) {
        if (issues.find(workspaceId, new IssueId(issueId)).isEmpty()) {
            return List.of();
        }
        List<Comment> found = comments.findByIssue(workspaceId, new IssueId(issueId));
        List<Actor> authors = new ArrayList<>();
        for (Comment comment : found) {
            if (comment.authorId() != null) {
                authors.add(new Actor(comment.authorType(), comment.authorId()));
            }
        }
        ActorNames.Lookup lookup = names.resolve(workspaceId, authors);
        return found.stream()
                .map(comment -> new CommentDetails(
                        comment.id().value(),
                        contract(comment.authorType()),
                        comment.authorId(),
                        lookup.author(comment.authorType(), comment.authorId()).name(),
                        comment.body(),
                        comment.createdAt()))
                .toList();
    }

    private static IssueDetails details(Issue issue) {
        Actor assignee = issue.assignee();
        return new IssueDetails(
                issue.id().value(),
                issue.workspaceId(),
                issue.number(),
                issue.title(),
                issue.description(),
                issue.status().code(),
                assignee == null ? null : contract(assignee.type()),
                assignee == null ? null : assignee.id());
    }

    private static ActorType contract(com.orchestragent.issue.domain.ActorType type) {
        return ActorType.valueOf(type.name());
    }
}
