package com.orchestragent.issue.infrastructure.adapter.in.web;

import com.orchestragent.issue.application.IssueService;
import com.orchestragent.issue.application.ReviewService;
import com.orchestragent.issue.application.IssueService.CreateIssue;
import com.orchestragent.issue.application.IssueService.UpdateIssue;
import com.orchestragent.issue.domain.IssueFilter;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.AssignRequest;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.CreateIssueRequest;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.IssueListResponse;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.IssueResponse;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.MoveRequest;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.RequestChangesRequest;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.UpdateIssueRequest;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/issues")
class IssueController {

    private static final int DEFAULT_LIMIT = 500;

    private final IssueService issues;
    private final ReviewService review;

    IssueController(IssueService issues, ReviewService review) {
        this.issues = issues;
        this.review = review;
    }

    /** Filters: repeat or comma-separate {@code status}, {@code priority} and {@code assignee}. */
    @GetMapping
    IssueListResponse list(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) List<UUID> assignee,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        IssueFilter filter = new IssueFilter(
                parse(status, Status::fromCode),
                parse(priority, Priority::fromCode),
                assignee == null ? Set.of() : new HashSet<>(assignee),
                unassigned);
        return IssueListResponse.from(issues.list(user.id(), workspaceId, filter, limit));
    }

    @PostMapping
    ResponseEntity<IssueResponse> create(
            CurrentUser user, @PathVariable UUID workspaceId, @Valid @RequestBody CreateIssueRequest request) {
        CreateIssue command = new CreateIssue(
                request.title(),
                request.description(),
                request.status() == null ? null : Status.fromCode(request.status()),
                request.priority() == null ? null : Priority.fromCode(request.priority()),
                request.assignee() == null ? null : request.assignee().toActor());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IssueResponse.from(issues.create(user.id(), workspaceId, command)));
    }

    @GetMapping("/{issueId}")
    IssueResponse get(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID issueId) {
        return IssueResponse.from(issues.get(user.id(), workspaceId, issueId));
    }

    @GetMapping("/number/{number}")
    IssueResponse getByNumber(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable int number) {
        return IssueResponse.from(issues.getByNumber(user.id(), workspaceId, number));
    }

    @PatchMapping("/{issueId}")
    IssueResponse update(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID issueId,
            @Valid @RequestBody UpdateIssueRequest request) {
        UpdateIssue command = new UpdateIssue(
                request.title(),
                request.description(),
                request.status() == null ? null : Status.fromCode(request.status()),
                request.priority() == null ? null : Priority.fromCode(request.priority()));
        return IssueResponse.from(issues.update(user.id(), workspaceId, issueId, command));
    }

    @PutMapping("/{issueId}/assignee")
    IssueResponse assign(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID issueId,
            @Valid @RequestBody AssignRequest request) {
        return IssueResponse.from(issues.assign(
                user.id(), workspaceId, issueId, request.assignee() == null ? null : request.assignee().toActor()));
    }

    @PostMapping("/{issueId}/move")
    IssueResponse move(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID issueId,
            @Valid @RequestBody MoveRequest request) {
        return IssueResponse.from(issues.move(
                user.id(), workspaceId, issueId, Status.fromCode(request.status()), request.aboveId(), request.belowId()));
    }

    /** The review gate: a person accepts the agent's work and the issue is done. */
    @PostMapping("/{issueId}/approve")
    IssueResponse approve(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID issueId) {
        return IssueResponse.from(review.approve(user.id(), workspaceId, issueId));
    }

    /** The review gate: a person sends the work back with a comment, and the agent is asked to rework it. */
    @PostMapping("/{issueId}/request-changes")
    IssueResponse requestChanges(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID issueId,
            @Valid @RequestBody RequestChangesRequest request) {
        return IssueResponse.from(review.requestChanges(user.id(), workspaceId, issueId, request.comment()));
    }

    @DeleteMapping("/{issueId}")
    ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID issueId) {
        issues.delete(user.id(), workspaceId, issueId);
        return ResponseEntity.noContent().build();
    }

    private static <T> Set<T> parse(List<String> codes, java.util.function.Function<String, T> parser) {
        if (codes == null) {
            return Set.of();
        }
        Set<T> parsed = new HashSet<>();
        codes.forEach(code -> parsed.add(parser.apply(code)));
        return parsed;
    }
}
