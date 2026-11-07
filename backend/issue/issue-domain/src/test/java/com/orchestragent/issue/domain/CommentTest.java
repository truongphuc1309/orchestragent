package com.orchestragent.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();
    private final IssueId issueId = IssueId.generate();

    @Test
    void aMemberCommentKeepsItsAuthorAndTrimsTheBody() {
        UUID author = UUID.randomUUID();

        Comment comment = Comment.write(workspaceId, issueId, ActorType.MEMBER, author, "  Looks good\n", NOW);

        assertThat(comment.body()).isEqualTo("Looks good");
        assertThat(comment.authorType()).isEqualTo(ActorType.MEMBER);
        assertThat(comment.authorId()).isEqualTo(author);
        assertThat(comment.issueId()).isEqualTo(issueId);
        assertThat(comment.createdAt()).isEqualTo(NOW);
    }

    @Test
    void commentsMayHaveSeveralLines() {
        Comment comment = Comment.write(workspaceId, issueId, ActorType.MEMBER, UUID.randomUUID(), "one\n\ntwo", NOW);

        assertThat(comment.body()).isEqualTo("one\n\ntwo");
    }

    @Test
    void aSystemCommentHasNoAuthorId() {
        Comment comment = Comment.write(workspaceId, issueId, ActorType.SYSTEM, null, "Task failed", NOW);

        assertThat(comment.authorId()).isNull();
    }

    @Test
    void onlySystemCommentsMayLackAnAuthorId() {
        assertThatThrownBy(() -> Comment.write(workspaceId, issueId, ActorType.MEMBER, null, "Hi", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comment.write(workspaceId, issueId, ActorType.SYSTEM, UUID.randomUUID(), "Hi", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankAndTooLongBodies() {
        assertThatThrownBy(() -> Comment.validBody("  \n ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Comment.validBody(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Comment.validBody("x".repeat(10_001)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("comment.invalid_body");
        assertThat(Comment.validBody("x".repeat(10_000))).hasSize(10_000);
    }
}
