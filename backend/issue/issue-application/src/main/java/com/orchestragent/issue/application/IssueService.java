package com.orchestragent.issue.application;

import com.orchestragent.issue.application.ActorNames.Lookup;
import com.orchestragent.issue.application.Views.IssuePageView;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.application.port.AgentDirectory;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueFilter;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueList;
import com.orchestragent.issue.domain.IssueNumbers;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.issue.domain.Positions;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, editing, ordering and deleting issues. Every method first checks that the caller belongs to the
 * workspace (a non-member gets "not found"), and every query is limited to that workspace.
 */
@Service
public class IssueService {

    /** Most issues one list call returns, whatever the caller asks for. */
    public static final int MAX_LIST_LIMIT = 1000;

    private final IssueRepository issues;
    private final IssueNumbers numbers;
    private final WorkspaceAccess access;
    private final AgentDirectory agents;
    private final ActorNames names;
    private final IssueEvents events;
    private final Clock clock;

    IssueService(
            IssueRepository issues,
            IssueNumbers numbers,
            WorkspaceAccess access,
            AgentDirectory agents,
            ActorNames names,
            IssueEvents events,
            Clock clock) {
        this.issues = issues;
        this.numbers = numbers;
        this.access = access;
        this.agents = agents;
        this.names = names;
        this.events = events;
        this.clock = clock;
    }

    /** What to put in a new issue. Null status and priority mean the defaults (todo, none). */
    public record CreateIssue(String title, String description, Status status, Priority priority, Actor assignee) {
    }

    /** Fields to change; null means "leave as it is". The assignee has its own call. */
    public record UpdateIssue(String title, String description, Status status, Priority priority) {
    }

    @Transactional
    public IssueView create(UUID userId, UUID workspaceId, CreateIssue command) {
        access.requireMember(workspaceId, userId);
        Status status = command.status() == null ? Status.TODO : command.status();
        Priority priority = command.priority() == null ? Priority.NONE : command.priority();
        requireAssignable(workspaceId, command.assignee());

        Issue issue = Issue.create(
                workspaceId,
                numbers.next(workspaceId),
                command.title(),
                command.description(),
                status,
                priority,
                command.assignee(),
                Actor.member(userId),
                bottomOf(workspaceId, status),
                clock.instant());
        issues.save(issue);

        events.created(issue);
        if (issue.assignee() != null) {
            events.assigneeChanged(issue, null, userId);
        }
        return view(issue);
    }

    @Transactional(readOnly = true)
    public IssueView get(UUID userId, UUID workspaceId, UUID issueId) {
        access.requireMember(workspaceId, userId);
        return view(load(workspaceId, issueId));
    }

    @Transactional(readOnly = true)
    public IssueView getByNumber(UUID userId, UUID workspaceId, int number) {
        access.requireMember(workspaceId, userId);
        return view(issues.findByNumber(workspaceId, number).orElseThrow(IssueService::notFound));
    }

    @Transactional(readOnly = true)
    public IssuePageView list(UUID userId, UUID workspaceId, IssueFilter filter, int limit) {
        access.requireMember(workspaceId, userId);
        IssueList found = issues.list(workspaceId, filter, Math.clamp(limit, 1, MAX_LIST_LIMIT));
        Lookup lookup = names.resolve(workspaceId, actorsOf(found.items()));
        List<IssueView> items = found.items().stream().map(issue -> view(issue, lookup)).toList();
        return new IssuePageView(items, found.total());
    }

    /** Edits title, description, status and priority. A new status puts the issue at the bottom of its column. */
    @Transactional
    public IssueView update(UUID userId, UUID workspaceId, UUID issueId, UpdateIssue command) {
        access.requireMember(workspaceId, userId);
        Issue issue = load(workspaceId, issueId);
        Instant now = clock.instant();

        Set<String> changed = new LinkedHashSet<>();
        if (command.title() != null && issue.rename(command.title(), now)) {
            changed.add("title");
        }
        if (command.description() != null && issue.describe(command.description(), now)) {
            changed.add("description");
        }
        if (command.priority() != null && issue.changePriority(command.priority(), now)) {
            changed.add("priority");
        }
        if (command.status() != null && command.status() != issue.status()) {
            issue.moveTo(command.status(), bottomOf(workspaceId, command.status()), now);
            changed.add("status");
            changed.add("position");
        }

        if (!changed.isEmpty()) {
            issues.save(issue);
            events.updated(issue, changed, userId);
        }
        return view(issue);
    }

    /** Assigns the issue to a member or an agent, or to nobody when {@code assignee} is null. */
    @Transactional
    public IssueView assign(UUID userId, UUID workspaceId, UUID issueId, Actor assignee) {
        access.requireMember(workspaceId, userId);
        Issue issue = load(workspaceId, issueId);
        requireAssignable(workspaceId, assignee);

        Actor previous = issue.assignee();
        if (issue.assign(assignee, clock.instant())) {
            issues.save(issue);
            events.assigneeChanged(issue, previous, userId);
        }
        return view(issue);
    }

    /**
     * Drags an issue to a column and a place in it, between two neighbours. {@code aboveId} is the issue that
     * will be directly above it and {@code belowId} the one directly below; either is null at the top or
     * bottom of the column (both null: append to the column). The server computes the position, so a client
     * can never write an arbitrary number.
     */
    @Transactional
    public IssueView move(UUID userId, UUID workspaceId, UUID issueId, Status status, UUID aboveId, UUID belowId) {
        access.requireMember(workspaceId, userId);
        Issue issue = load(workspaceId, issueId);
        double position = positionBetween(workspaceId, issue, status, aboveId, belowId);

        Status previousStatus = issue.status();
        if (issue.moveTo(status, position, clock.instant())) {
            issues.save(issue);
            Set<String> changed = new LinkedHashSet<>();
            if (previousStatus != status) {
                changed.add("status");
            }
            changed.add("position");
            events.updated(issue, changed, userId);
        }
        return view(issue);
    }

    /** Only the person who created the issue, or an admin or owner, may delete it. */
    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID issueId) {
        WorkspaceRole role = access.requireRole(workspaceId, userId, WorkspaceRole.MEMBER);
        Issue issue = load(workspaceId, issueId);
        boolean isCreator = issue.creator().equals(Actor.member(userId));
        if (!isCreator && !role.atLeast(WorkspaceRole.ADMIN)) {
            throw new ForbiddenException(
                    "issue.delete_forbidden", "Only the creator, an admin or an owner can delete this issue");
        }
        issues.delete(issue);
        events.deleted(issue);
    }

    // ---- positions ---------------------------------------------------------------------------------------

    private double bottomOf(UUID workspaceId, Status status) {
        OptionalDouble max = issues.maxPosition(workspaceId, status);
        return Positions.append(max.isPresent() ? max.getAsDouble() : null);
    }

    private double positionBetween(UUID workspaceId, Issue moving, Status target, UUID aboveId, UUID belowId) {
        if (aboveId == null && belowId == null) {
            return bottomOf(workspaceId, target);
        }
        Double above = neighbourPosition(workspaceId, moving, target, aboveId);
        Double below = neighbourPosition(workspaceId, moving, target, belowId);
        OptionalDouble position = between(above, below);
        if (position.isPresent()) {
            return position.getAsDouble();
        }

        // The two neighbours are too close to split: space the whole column out again, then try once more.
        Map<UUID, Double> renumbered = renumberColumn(workspaceId, moving, target);
        return between(
                        aboveId == null ? null : renumbered.get(aboveId),
                        belowId == null ? null : renumbered.get(belowId))
                .orElseThrow(() -> new IllegalStateException("No room between neighbours after renumbering"));
    }

    private Double neighbourPosition(UUID workspaceId, Issue moving, Status target, UUID neighbourId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(moving.id().value())) {
            throw new ValidationException("issue.invalid_move", "An issue cannot be placed next to itself");
        }
        Issue neighbour = issues.find(workspaceId, new IssueId(neighbourId)).orElseThrow(IssueService::staleMove);
        if (neighbour.status() != target) {
            throw staleMove();
        }
        return neighbour.position();
    }

    private static OptionalDouble between(Double above, Double below) {
        try {
            return Positions.between(above, below);
        } catch (IllegalArgumentException wrongOrder) {
            throw staleMove();
        }
    }

    /** Renumbers the column without the moving issue and returns the new positions by issue ID. */
    private Map<UUID, Double> renumberColumn(UUID workspaceId, Issue moving, Status target) {
        Map<UUID, Double> positions = new HashMap<>();
        double next = Positions.STEP;
        for (Issue other : issues.findByStatus(workspaceId, target)) {
            if (other.id().equals(moving.id())) {
                continue;
            }
            other.renumber(next);
            issues.save(other);
            positions.put(other.id().value(), next);
            next += Positions.STEP;
        }
        return positions;
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private void requireAssignable(UUID workspaceId, Actor assignee) {
        if (assignee == null) {
            return;
        }
        boolean valid = assignee.type() == ActorType.MEMBER
                ? access.isMember(workspaceId, assignee.id())
                : agents.exists(workspaceId, assignee.id());
        if (!valid) {
            throw new ValidationException("issue.invalid_assignee", "The assignee does not exist in this workspace");
        }
    }

    private Issue load(UUID workspaceId, UUID issueId) {
        return issues.find(workspaceId, new IssueId(issueId)).orElseThrow(IssueService::notFound);
    }

    private IssueView view(Issue issue) {
        return view(issue, names.resolve(issue.workspaceId(), actorsOf(List.of(issue))));
    }

    private static IssueView view(Issue issue, Lookup lookup) {
        return new IssueView(
                issue.id().value(),
                issue.workspaceId(),
                issue.number(),
                issue.title(),
                issue.description(),
                issue.status(),
                issue.priority(),
                lookup.view(issue.assignee()),
                lookup.view(issue.creator()),
                issue.position(),
                issue.createdAt(),
                issue.updatedAt());
    }

    private static List<Actor> actorsOf(List<Issue> items) {
        List<Actor> actors = new ArrayList<>();
        for (Issue issue : items) {
            actors.add(issue.assignee());
            actors.add(issue.creator());
        }
        return actors;
    }

    static NotFoundException notFound() {
        return new NotFoundException("issue.not_found", "Issue not found");
    }

    private static ConflictException staleMove() {
        return new ConflictException("issue.stale_move", "The board changed in the meantime. Reload and try again.");
    }
}
