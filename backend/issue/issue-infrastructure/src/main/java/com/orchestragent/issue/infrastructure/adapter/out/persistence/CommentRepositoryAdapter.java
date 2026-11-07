package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.CommentId;
import com.orchestragent.issue.domain.CommentRepository;
import com.orchestragent.issue.domain.IssueId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CommentRepositoryAdapter implements CommentRepository {

    private final CommentJpaRepository jpa;

    CommentRepositoryAdapter(CommentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Comment comment) {
        jpa.save(new CommentEntity(
                comment.id().value(),
                comment.workspaceId(),
                comment.issueId().value(),
                comment.authorType().code(),
                comment.authorId(),
                comment.body(),
                comment.createdAt()));
    }

    @Override
    public List<Comment> findByIssue(UUID workspaceId, IssueId issueId) {
        return jpa.findByWorkspaceIdAndIssueIdOrderByCreatedAtAscIdAsc(workspaceId, issueId.value()).stream()
                .map(CommentRepositoryAdapter::toDomain)
                .toList();
    }

    private static Comment toDomain(CommentEntity entity) {
        return new Comment(
                new CommentId(entity.getId()),
                entity.getWorkspaceId(),
                new IssueId(entity.getIssueId()),
                ActorType.fromCode(entity.getAuthorType()),
                entity.getAuthorId(),
                entity.getBody(),
                entity.getCreatedAt());
    }
}
