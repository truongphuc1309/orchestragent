package com.orchestragent.issue.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueId;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What an issue does when the work of its agent starts, finishes or fails. */
class AgentRunReactionsTest {

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
    private final AgentRunReactions reactions = new AgentRunReactions(issues, comments, events, Fakes.clock());

    private final UUID workspace = UUID.randomUUID();
    private final UUID member = users.add("Mia").id();
    private final UUID agentId = agents.add("Builder");
    private final IssueView issue;

    {
        access.join(workspace, member, WorkspaceRole.MEMBER);
        issue = issueService.create(member, workspace, new CreateIssue(projects.defaultIn(workspace), "Fix it", null, Status.TODO, null, null));
        published.clear();
    }

    private Issue stored() {
        return issues.find(workspace, new IssueId(issue.id())).orElseThrow();
    }

    private List<Comment> commentsOnIssue() {
        return comments.findByIssue(workspace, new IssueId(issue.id()));
    }

    // ---- started -----------------------------------------------------------------------------------------

    @Test
    void whenTheAgentStartsTheIssueMovesToInProgress() {
        reactions.taskStarted(workspace, issue.id(), agentId);

        assertThat(stored().status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(published).filteredOn(IssueUpdated.class::isInstance).singleElement().satisfies(event -> {
            IssueUpdated updated = (IssueUpdated) event;
            assertThat(updated.status()).isEqualTo("in_progress");
            assertThat(updated.changedFields()).contains("status");
            assertThat(updated.actorType()).isEqualTo(ActorType.AGENT);
            assertThat(updated.actorId()).isEqualTo(agentId);
        });
    }

    @Test
    void startingAgainChangesNothingAndPublishesNothing() {
        reactions.taskStarted(workspace, issue.id(), agentId);
        published.clear();

        reactions.taskStarted(workspace, issue.id(), agentId);

        assertThat(published).isEmpty();
    }

    @Test
    void anIssueThatIsDoneGoesBackToInProgressWhenTheAgentWorksOnItAgain() {
        issues.find(workspace, new IssueId(issue.id())).orElseThrow().moveTo(Status.DONE, 1, Fakes.clock().instant());

        reactions.taskStarted(workspace, issue.id(), agentId);

        assertThat(stored().status()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void theMovedIssueGoesToTheBottomOfItsColumn() {
        IssueView waiting = issueService.create(member, workspace, new CreateIssue(projects.defaultIn(workspace), "Already working", null, Status.IN_PROGRESS, null, null));

        reactions.taskStarted(workspace, issue.id(), agentId);

        assertThat(stored().position()).isGreaterThan(issues.find(workspace, new IssueId(waiting.id())).orElseThrow().position());
    }

    // ---- completed ---------------------------------------------------------------------------------------

    @Test
    void whenTheAgentCompletesItsResultBecomesACommentOfTheAgentAndTheIssueGoesToReview() {
        UUID eventId = UUID.randomUUID();

        reactions.taskCompleted(eventId, workspace, issue.id(), agentId, "  I fixed the login bug.\n\nSee the diff. ");

        assertThat(stored().status()).isEqualTo(Status.IN_REVIEW);
        assertThat(commentsOnIssue()).singleElement().satisfies(comment -> {
            assertThat(comment.authorType()).isEqualTo(com.orchestragent.issue.domain.ActorType.AGENT);
            assertThat(comment.authorId()).isEqualTo(agentId);
            assertThat(comment.body()).isEqualTo("I fixed the login bug.\n\nSee the diff.");
            assertThat(comment.sourceEventId()).isEqualTo(eventId);
        });
        assertThat(published).filteredOn(CommentAdded.class::isInstance).singleElement().satisfies(event -> {
            CommentAdded added = (CommentAdded) event;
            assertThat(added.authorType()).isEqualTo(ActorType.AGENT);
            assertThat(added.changesRequested()).isFalse();
        });
    }

    @Test
    void handlingTheSameCompletionTwiceWritesOneComment() {
        UUID eventId = UUID.randomUUID();
        reactions.taskCompleted(eventId, workspace, issue.id(), agentId, "Result");

        reactions.taskCompleted(eventId, workspace, issue.id(), agentId, "Result");

        assertThat(commentsOnIssue()).hasSize(1);
        assertThat(stored().status()).isEqualTo(Status.IN_REVIEW);
    }

    @Test
    void aCompletionWithoutTextStillMakesACommentSoTheReviewHasSomethingToStartFrom() {
        reactions.taskCompleted(UUID.randomUUID(), workspace, issue.id(), agentId, "   ");

        assertThat(commentsOnIssue()).singleElement().satisfies(comment -> assertThat(comment.body()).isEqualTo(AgentRunReactions.NO_RESULT));
    }

    @Test
    void aHugeResultIsCutToFitAComment() {
        reactions.taskCompleted(UUID.randomUUID(), workspace, issue.id(), agentId, "x".repeat(Comment.MAX_BODY_LENGTH + 5000));

        assertThat(commentsOnIssue()).singleElement().satisfies(comment ->
                assertThat(comment.body()).hasSize(Comment.MAX_BODY_LENGTH).endsWith("(cut: too long)"));
    }

    @Test
    void aCommentOfAnAgentIsNotAReasonToStartMoreWork() {
        reactions.taskCompleted(UUID.randomUUID(), workspace, issue.id(), agentId, "Done");

        // The planner ignores comments of agents; here we only check that the event says so.
        assertThat(published).filteredOn(CommentAdded.class::isInstance).allSatisfy(event ->
                assertThat(((CommentAdded) event).authorType()).isNotEqualTo(ActorType.MEMBER));
    }

    // ---- failed ------------------------------------------------------------------------------------------

    @Test
    void whenTheAgentFailsASystemCommentSaysWhyAndTheIssueIsBlocked() {
        UUID eventId = UUID.randomUUID();

        reactions.taskFailed(eventId, workspace, issue.id(), agentId, "timeout", "The tool did not answer for 30 minutes.");

        assertThat(stored().status()).isEqualTo(Status.BLOCKED);
        assertThat(commentsOnIssue()).singleElement().satisfies(comment -> {
            assertThat(comment.authorType()).isEqualTo(com.orchestragent.issue.domain.ActorType.SYSTEM);
            assertThat(comment.authorId()).isNull();
            assertThat(comment.body())
                    .startsWith("The agent could not finish this task: it ran out of time.")
                    .contains("The tool did not answer for 30 minutes.");
            assertThat(comment.sourceEventId()).isEqualTo(eventId);
        });
        assertThat(published).filteredOn(CommentAdded.class::isInstance).singleElement().satisfies(event ->
                assertThat(((CommentAdded) event).authorType()).isEqualTo(ActorType.SYSTEM));
    }

    @Test
    void handlingTheSameFailureTwiceWritesOneComment() {
        UUID eventId = UUID.randomUUID();
        reactions.taskFailed(eventId, workspace, issue.id(), agentId, "agent_error", "boom");

        reactions.taskFailed(eventId, workspace, issue.id(), agentId, "agent_error", "boom");

        assertThat(commentsOnIssue()).hasSize(1);
    }

    @Test
    void theFailureTextNamesTheReasonInPlainWords() {
        assertThat(AgentRunReactions.failureComment("runtime_offline", "")).isEqualTo("The agent could not finish this task: its computer went offline.");
        assertThat(AgentRunReactions.failureComment("agent_error", "It crashed")).isEqualTo("The agent could not finish this task: the agent reported an error.\n\nIt crashed");
        assertThat(AgentRunReactions.failureComment(null, null)).isEqualTo("The agent could not finish this task: the agent reported an error.");
        assertThat(AgentRunReactions.failureComment("disk_full", "")).isEqualTo("The agent could not finish this task: disk full.");
    }

    @Test
    void aHugeErrorIsCutToFitAComment() {
        String comment = AgentRunReactions.failureComment("agent_error", "e".repeat(20_000));

        assertThat(comment).hasSize(Comment.MAX_BODY_LENGTH).endsWith("(cut: too long)");
    }

    // ---- the issue is gone -------------------------------------------------------------------------------

    @Test
    void anIssueDeletedWhileTheAgentWasWorkingIsLeftAlone() {
        UUID gone = UUID.randomUUID();

        reactions.taskStarted(workspace, gone, agentId);
        reactions.taskCompleted(UUID.randomUUID(), workspace, gone, agentId, "Done");
        reactions.taskFailed(UUID.randomUUID(), workspace, gone, agentId, "agent_error", "boom");

        assertThat(comments.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void anIssueOfAnotherWorkspaceIsNeverTouched() {
        reactions.taskStarted(UUID.randomUUID(), issue.id(), agentId);
        reactions.taskCompleted(UUID.randomUUID(), UUID.randomUUID(), issue.id(), agentId, "Done");

        assertThat(stored().status()).isEqualTo(Status.TODO);
        assertThat(comments.stored).isEmpty();
    }
}
