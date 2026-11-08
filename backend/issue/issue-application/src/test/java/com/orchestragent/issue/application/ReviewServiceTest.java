package com.orchestragent.issue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.issue.application.Fakes.FakeAccess;
import com.orchestragent.issue.application.Fakes.FakeAgents;
import com.orchestragent.issue.application.Fakes.FakeUsers;
import com.orchestragent.issue.application.Fakes.InMemoryComments;
import com.orchestragent.issue.application.Fakes.InMemoryIssues;
import com.orchestragent.issue.application.Fakes.InMemoryNumbers;
import com.orchestragent.issue.application.Fakes.InMemoryProjects;
import com.orchestragent.issue.application.IssueService.CreateIssue;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueUpdated;
import com.orchestragent.issue.domain.Comment;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The review gate: a person decides what happens to work an agent finished. */
class ReviewServiceTest {

    private final InMemoryIssues issues = new InMemoryIssues();

    private final InMemoryProjects projects = new InMemoryProjects();
    private final InMemoryComments comments = new InMemoryComments();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final FakeAgents agents = new FakeAgents();
    private final List<Object> published = new ArrayList<>();
    private final IssueEvents events = new IssueEvents(published::add, Fakes.clock());
    private final IssueService issueService =
            new IssueService(issues, projects, new InMemoryNumbers(projects), access, agents, new ActorNames(users, agents), events, Fakes.clock());
    private final ReviewService service = new ReviewService(issues, comments, access, issueService, events, Fakes.clock());

    private final UUID workspace = UUID.randomUUID();
    private final UUID otherWorkspace = UUID.randomUUID();
    private final UUID reviewer = users.add("Rita").id();
    private final UUID outsider = users.add("Otto").id();

    {
        access.join(workspace, reviewer, WorkspaceRole.MEMBER);
        access.join(otherWorkspace, outsider, WorkspaceRole.OWNER);
        access.join(otherWorkspace, reviewer, WorkspaceRole.MEMBER);
    }

    private IssueView issueIn(Status status) {
        IssueView created = issueService.create(reviewer, workspace, new CreateIssue(projects.defaultIn(workspace), "Work", null, status, null, null));
        published.clear();
        return created;
    }

    private Status statusOf(IssueView issue) {
        return issues.find(workspace, new IssueId(issue.id())).orElseThrow().status();
    }

    // ---- approve -----------------------------------------------------------------------------------------

    @Test
    void approvingMovesAnIssueInReviewToDone() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        IssueView approved = service.approve(reviewer, workspace, issue.id());

        assertThat(approved.status()).isEqualTo(Status.DONE);
        assertThat(statusOf(issue)).isEqualTo(Status.DONE);
        assertThat(published).filteredOn(IssueUpdated.class::isInstance).singleElement().satisfies(event -> {
            IssueUpdated updated = (IssueUpdated) event;
            assertThat(updated.status()).isEqualTo("done");
            assertThat(updated.actorType()).isEqualTo(ActorType.MEMBER);
            assertThat(updated.actorId()).isEqualTo(reviewer);
        });
    }

    @Test
    void theApprovedIssueGoesToTheBottomOfTheDoneColumn() {
        IssueView earlier = issueService.create(reviewer, workspace, new CreateIssue(projects.defaultIn(workspace), "Earlier", null, Status.DONE, null, null));
        IssueView issue = issueIn(Status.IN_REVIEW);

        service.approve(reviewer, workspace, issue.id());

        assertThat(issues.find(workspace, new IssueId(issue.id())).orElseThrow().position())
                .isGreaterThan(issues.find(workspace, new IssueId(earlier.id())).orElseThrow().position());
    }

    @Test
    void onlyAnIssueInReviewCanBeApproved() {
        for (Status status : Status.values()) {
            if (status == Status.IN_REVIEW) {
                continue;
            }
            IssueView issue = issueIn(status);

            assertThatThrownBy(() -> service.approve(reviewer, workspace, issue.id()))
                    .as(status.code())
                    .isInstanceOf(ConflictException.class)
                    .extracting("code")
                    .isEqualTo("issue.not_in_review");
            assertThat(statusOf(issue)).isEqualTo(status);
        }
        assertThat(published).isEmpty();
    }

    @Test
    void approvingTwiceIsAConflictTheSecondTime() {
        IssueView issue = issueIn(Status.IN_REVIEW);
        service.approve(reviewer, workspace, issue.id());

        assertThatThrownBy(() -> service.approve(reviewer, workspace, issue.id())).isInstanceOf(ConflictException.class);
    }

    // ---- request changes ---------------------------------------------------------------------------------

    @Test
    void requestingChangesSendsTheIssueBackToTodoWithTheReviewersComment() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        IssueView sentBack = service.requestChanges(reviewer, workspace, issue.id(), "  Please add a test.\n\nAnd fix the typo. ");

        assertThat(sentBack.status()).isEqualTo(Status.TODO);
        assertThat(statusOf(issue)).isEqualTo(Status.TODO);
        List<Comment> written = comments.findByIssue(workspace, new IssueId(issue.id()));
        assertThat(written).singleElement().satisfies(comment -> {
            assertThat(comment.body()).isEqualTo("Please add a test.\n\nAnd fix the typo.");
            assertThat(comment.authorType()).isEqualTo(com.orchestragent.issue.domain.ActorType.MEMBER);
            assertThat(comment.authorId()).isEqualTo(reviewer);
        });
    }

    @Test
    void theCommentEventSaysChangesWereRequestedSoTheAgentIsAskedToReworkItsResult() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        service.requestChanges(reviewer, workspace, issue.id(), "Redo it.");

        assertThat(published).filteredOn(CommentAdded.class::isInstance).singleElement().satisfies(event -> {
            CommentAdded added = (CommentAdded) event;
            assertThat(added.changesRequested()).isTrue();
            assertThat(added.authorType()).isEqualTo(ActorType.MEMBER);
        });
        assertThat(published).filteredOn(IssueUpdated.class::isInstance).singleElement().satisfies(event ->
                assertThat(((IssueUpdated) event).status()).isEqualTo("todo"));
    }

    @Test
    void theCommentIsRequiredAndNothingChangesWithoutIt() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        for (String blank : new String[] {null, "", "   \n  "}) {
            assertThatThrownBy(() -> service.requestChanges(reviewer, workspace, issue.id(), blank))
                    .isInstanceOf(ValidationException.class);
        }

        assertThat(statusOf(issue)).isEqualTo(Status.IN_REVIEW);
        assertThat(comments.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void anOverlongCommentIsRefused() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        assertThatThrownBy(() -> service.requestChanges(reviewer, workspace, issue.id(), "x".repeat(Comment.MAX_BODY_LENGTH + 1)))
                .isInstanceOf(ValidationException.class);
        assertThat(statusOf(issue)).isEqualTo(Status.IN_REVIEW);
    }

    @Test
    void onlyAnIssueInReviewCanBeSentBackAndNoCommentIsLeftBehind() {
        IssueView issue = issueIn(Status.DONE);

        assertThatThrownBy(() -> service.requestChanges(reviewer, workspace, issue.id(), "Redo it."))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("issue.not_in_review");

        assertThat(comments.stored).isEmpty();
        assertThat(statusOf(issue)).isEqualTo(Status.DONE);
    }

    // ---- who may review ----------------------------------------------------------------------------------

    @Test
    void anOutsiderCannotApproveOrSendBackAnything() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        assertThatThrownBy(() -> service.approve(outsider, workspace, issue.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requestChanges(outsider, workspace, issue.id(), "No."))
                .isInstanceOf(NotFoundException.class);

        assertThat(statusOf(issue)).isEqualTo(Status.IN_REVIEW);
        assertThat(comments.stored).isEmpty();
    }

    @Test
    void anIssueOfAnotherWorkspaceCannotBeReviewedThroughThisOne() {
        IssueView issue = issueIn(Status.IN_REVIEW);

        assertThatThrownBy(() -> service.approve(reviewer, otherWorkspace, issue.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requestChanges(reviewer, otherWorkspace, issue.id(), "No."))
                .isInstanceOf(NotFoundException.class);
        assertThat(statusOf(issue)).isEqualTo(Status.IN_REVIEW);
    }

    @Test
    void anUnknownIssueIsNotFound() {
        assertThatThrownBy(() -> service.approve(reviewer, workspace, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
