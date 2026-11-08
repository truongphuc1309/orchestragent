package com.orchestragent.issue.infrastructure.adapter.in.web;

import com.orchestragent.issue.application.Views.ActorView;
import com.orchestragent.issue.application.Views.CommentView;
import com.orchestragent.issue.application.Views.IssuePageView;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.shared.error.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire format of the issue REST API. Status, priority and actor types travel as lower-case strings. */
final class IssueDtos {

    private IssueDtos() {
    }

    record AssigneeRequest(@NotNull String type, @NotNull UUID id) {

        Actor toActor() {
            ActorType parsed = ActorType.fromCode(type);
            if (parsed == ActorType.SYSTEM) {
                throw new ValidationException("issue.invalid_assignee", "An issue cannot be assigned to the system");
            }
            return new Actor(parsed, id);
        }
    }

    record CreateIssueRequest(
            @NotNull UUID projectId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20_000) String description,
            String status,
            String priority,
            @Valid AssigneeRequest assignee) {
    }

    /** Every field is optional; a missing field is left as it is. */
    record UpdateIssueRequest(
            @Size(max = 200) String title, @Size(max = 20_000) String description, String status, String priority) {
    }

    /** {@code assignee} null means "unassign". */
    record AssignRequest(@Valid AssigneeRequest assignee) {
    }

    record MoveRequest(@NotNull String status, UUID aboveId, UUID belowId) {
    }

    /** The reviewer's comment is required: sending work back without saying why helps nobody. */
    record RequestChangesRequest(@NotBlank @Size(max = 10_000) String comment) {
    }

    record CommentRequest(@NotBlank @Size(max = 10_000) String body) {
    }

    record ActorResponse(String type, UUID id, String name) {

        static ActorResponse from(ActorView view) {
            return view == null ? null : new ActorResponse(view.type().code(), view.id(), view.name());
        }
    }

    record IssueResponse(
            UUID id,
            UUID projectId,
            String key,
            int number,
            String title,
            String description,
            String status,
            String priority,
            ActorResponse assignee,
            ActorResponse creator,
            double position,
            Instant createdAt,
            Instant updatedAt) {

        static IssueResponse from(IssueView view) {
            return new IssueResponse(
                    view.id(),
                    view.projectId(),
                    view.key(),
                    view.number(),
                    view.title(),
                    view.description(),
                    view.status().code(),
                    view.priority().code(),
                    ActorResponse.from(view.assignee()),
                    ActorResponse.from(view.creator()),
                    view.position(),
                    view.createdAt(),
                    view.updatedAt());
        }
    }

    record IssueListResponse(List<IssueResponse> items, long total) {

        static IssueListResponse from(IssuePageView page) {
            return new IssueListResponse(page.items().stream().map(IssueResponse::from).toList(), page.total());
        }
    }

    record CommentResponse(UUID id, UUID issueId, ActorResponse author, String body, Instant createdAt) {

        static CommentResponse from(CommentView view) {
            return new CommentResponse(
                    view.id(), view.issueId(), ActorResponse.from(view.author()), view.body(), view.createdAt());
        }
    }
}
