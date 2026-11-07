package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueFilter;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.IssueList;
import com.orchestragent.issue.domain.IssueRepository;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Status;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class IssueRepositoryAdapter implements IssueRepository {

    private final IssueJpaRepository jpa;

    IssueRepositoryAdapter(IssueJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Issue issue) {
        jpa.findById(issue.id().value()).ifPresentOrElse(
                existing -> existing.update(
                        issue.title(),
                        issue.description(),
                        issue.status().code(),
                        issue.priority().code(),
                        assigneeType(issue),
                        issue.assignee() == null ? null : issue.assignee().id(),
                        issue.position(),
                        issue.updatedAt()),
                () -> jpa.save(new IssueEntity(
                        issue.id().value(),
                        issue.workspaceId(),
                        issue.number(),
                        issue.title(),
                        issue.description(),
                        issue.status().code(),
                        issue.priority().code(),
                        assigneeType(issue),
                        issue.assignee() == null ? null : issue.assignee().id(),
                        issue.creator().type().code(),
                        issue.creator().id(),
                        issue.position(),
                        issue.createdAt(),
                        issue.updatedAt())));
    }

    @Override
    public void delete(Issue issue) {
        jpa.deleteById(issue.id().value());
    }

    @Override
    public Optional<Issue> find(UUID workspaceId, IssueId id) {
        return jpa.findByIdAndWorkspaceId(id.value(), workspaceId).map(IssueRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Issue> findByNumber(UUID workspaceId, int number) {
        return jpa.findByWorkspaceIdAndNumber(workspaceId, number).map(IssueRepositoryAdapter::toDomain);
    }

    @Override
    public IssueList list(UUID workspaceId, IssueFilter filter, int limit) {
        Page<IssueEntity> page =
                jpa.findAll(matching(workspaceId, filter), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "number")));
        return new IssueList(page.getContent().stream().map(IssueRepositoryAdapter::toDomain).toList(), page.getTotalElements());
    }

    @Override
    public List<Issue> findByStatus(UUID workspaceId, Status status) {
        return jpa.findByWorkspaceIdAndStatusOrderByPositionAscNumberAsc(workspaceId, status.code()).stream()
                .map(IssueRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public OptionalDouble maxPosition(UUID workspaceId, Status status) {
        return jpa.maxPosition(workspaceId, status.code())
                .map(OptionalDouble::of)
                .orElseGet(OptionalDouble::empty);
    }

    private static Specification<IssueEntity> matching(UUID workspaceId, IssueFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(builder.equal(root.get("workspaceId"), workspaceId));
            if (!filter.statuses().isEmpty()) {
                all.add(root.get("status").in(filter.statuses().stream().map(Status::code).toList()));
            }
            if (!filter.priorities().isEmpty()) {
                all.add(root.get("priority").in(filter.priorities().stream().map(Priority::code).toList()));
            }
            if (filter.filtersAssignee()) {
                List<Predicate> any = new ArrayList<>();
                if (!filter.assigneeIds().isEmpty()) {
                    any.add(root.get("assigneeId").in(filter.assigneeIds()));
                }
                if (filter.unassigned()) {
                    any.add(builder.isNull(root.get("assigneeId")));
                }
                all.add(builder.or(any.toArray(Predicate[]::new)));
            }
            return builder.and(all.toArray(Predicate[]::new));
        };
    }

    private static String assigneeType(Issue issue) {
        return issue.assignee() == null ? null : issue.assignee().type().code();
    }

    private static Issue toDomain(IssueEntity entity) {
        Actor assignee = entity.getAssigneeId() == null
                ? null
                : new Actor(ActorType.fromCode(entity.getAssigneeType()), entity.getAssigneeId());
        return new Issue(
                new IssueId(entity.getId()),
                entity.getWorkspaceId(),
                entity.getNumber(),
                entity.getTitle(),
                entity.getDescription(),
                Status.fromCode(entity.getStatus()),
                Priority.fromCode(entity.getPriority()),
                assignee,
                new Actor(ActorType.fromCode(entity.getCreatorType()), entity.getCreatorId()),
                entity.getPosition(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
