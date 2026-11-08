package com.orchestragent.issue.application;

import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueCreated;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueUpdated;
import com.orchestragent.issue.contract.ProjectChanged;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.Project;
import com.orchestragent.shared.id.UuidV7;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes the integration events of the issue context. They go out inside the caller's transaction, so
 * the Modulith event registry stores them together with the change and retries delivery after a crash.
 */
@Component
class IssueEvents {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    IssueEvents(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    void created(Issue issue) {
        publisher.publishEvent(new IssueCreated(
                UuidV7.next(),
                clock.instant(),
                issue.workspaceId(),
                issue.projectId().value(),
                issue.id().value(),
                issue.key().toString(),
                contract(issue.creator()),
                issue.creator().id()));
    }

    void updated(Issue issue, Set<String> changedFields, UUID actorId) {
        updated(issue, changedFields, ActorType.MEMBER, actorId);
    }

    /** For changes an agent causes: {@code actorType} says who, {@code actorId} is the agent. */
    void updated(Issue issue, Set<String> changedFields, ActorType actorType, UUID actorId) {
        publisher.publishEvent(new IssueUpdated(
                UuidV7.next(),
                clock.instant(),
                issue.workspaceId(),
                issue.id().value(),
                changedFields,
                issue.status().code(),
                actorType,
                actorId));
    }

    void assigneeChanged(Issue issue, Actor previous, UUID actorId) {
        Actor current = issue.assignee();
        publisher.publishEvent(new IssueAssigneeChanged(
                UuidV7.next(),
                clock.instant(),
                issue.workspaceId(),
                issue.id().value(),
                previous == null ? null : contract(previous),
                previous == null ? null : previous.id(),
                current == null ? null : contract(current),
                current == null ? null : current.id(),
                ActorType.MEMBER,
                actorId));
    }

    void deleted(Issue issue) {
        publisher.publishEvent(new IssueDeleted(
                UuidV7.next(), clock.instant(), issue.workspaceId(), issue.id().value(), issue.key().toString()));
    }

    void projectChanged(Project project) {
        publisher.publishEvent(
                new ProjectChanged(UuidV7.next(), clock.instant(), project.workspaceId(), project.id().value()));
    }

    void commentAdded(Comment comment) {
        commentAdded(comment, false);
    }

    /** {@code changesRequested}: a reviewer wrote it when sending the issue back from review. */
    void commentAdded(Comment comment, boolean changesRequested) {
        publisher.publishEvent(new CommentAdded(
                UuidV7.next(),
                clock.instant(),
                comment.workspaceId(),
                comment.issueId().value(),
                comment.id().value(),
                contract(comment.authorType()),
                comment.authorId(),
                changesRequested));
    }

    private static ActorType contract(Actor actor) {
        return contract(actor.type());
    }

    private static ActorType contract(com.orchestragent.issue.domain.ActorType type) {
        return ActorType.valueOf(type.name());
    }
}
