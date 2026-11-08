package com.orchestragent.issue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.Fakes.FakeAccess;
import com.orchestragent.issue.application.Fakes.FakeAgents;
import com.orchestragent.issue.application.Fakes.FakeUsers;
import com.orchestragent.issue.application.Fakes.InMemoryComments;
import com.orchestragent.issue.application.Fakes.InMemoryIssues;
import com.orchestragent.issue.application.Fakes.InMemoryNumbers;
import com.orchestragent.issue.application.Fakes.InMemoryProjects;
import com.orchestragent.issue.application.IssueService.CreateIssue;
import com.orchestragent.issue.application.Views.CommentView;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentServiceTest {

    private final InMemoryIssues issues = new InMemoryIssues();

    private final InMemoryProjects projects = new InMemoryProjects();
    private final InMemoryComments comments = new InMemoryComments();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final FakeAgents agents = new FakeAgents();
    private final List<Object> published = new ArrayList<>();
    private final IssueEvents events = new IssueEvents(published::add, Fakes.clock());
    private final ActorNames names = new ActorNames(users, agents);
    private final IssueService issueService =
            new IssueService(issues, projects, new InMemoryNumbers(projects), access, agents, names, events, Fakes.clock());
    private final CommentService service = new CommentService(comments, issues, access, names, events, Fakes.clock());

    private final UserView member = users.add("Mia");
    private final UserView colleague = users.add("Max");
    private final UserView outsider = users.add("Otto");
    private final UUID workspace = UUID.randomUUID();
    private final UUID otherWorkspace = UUID.randomUUID();
    private final IssueView issue;

    {
        access.join(workspace, member.id(), WorkspaceRole.MEMBER);
        access.join(workspace, colleague.id(), WorkspaceRole.MEMBER);
        access.join(otherWorkspace, outsider.id(), WorkspaceRole.OWNER);
        issue = issueService.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Fix", null, null, null, null));
        published.clear();
    }

    @Test
    void addsACommentAndPublishesCommentAdded() {
        CommentView added = service.add(member.id(), workspace, issue.id(), "  Looks good  ");

        assertThat(added.body()).isEqualTo("Looks good");
        assertThat(added.author().name()).isEqualTo("Mia");
        assertThat(added.author().id()).isEqualTo(member.id());
        assertThat(added.issueId()).isEqualTo(issue.id());
        assertThat(published).singleElement().isInstanceOfSatisfying(CommentAdded.class, event -> {
            assertThat(event.workspaceId()).isEqualTo(workspace);
            assertThat(event.issueId()).isEqualTo(issue.id());
            assertThat(event.commentId()).isEqualTo(added.id());
            assertThat(event.authorType()).isEqualTo(ActorType.MEMBER);
            assertThat(event.authorId()).isEqualTo(member.id());
        });
    }

    @Test
    void listsCommentsOldestFirstWithAuthorNames() {
        service.add(member.id(), workspace, issue.id(), "first");
        service.add(colleague.id(), workspace, issue.id(), "second");

        assertThat(service.list(colleague.id(), workspace, issue.id()))
                .extracting(c -> c.author().name(), CommentView::body)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Mia", "first"),
                        org.assertj.core.api.Assertions.tuple("Max", "second"));
    }

    @Test
    void commentsOfOtherIssuesAreNotListed() {
        IssueView another = issueService.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Other", null, null, null, null));
        service.add(member.id(), workspace, issue.id(), "on the first");
        service.add(member.id(), workspace, another.id(), "on the other");

        assertThat(service.list(member.id(), workspace, issue.id())).extracting(CommentView::body).containsExactly("on the first");
    }

    @Test
    void aSystemCommentShowsAsTheSystem() {
        comments.save(com.orchestragent.issue.domain.Comment.write(
                workspace,
                new com.orchestragent.issue.domain.IssueId(issue.id()),
                com.orchestragent.issue.domain.ActorType.SYSTEM,
                null,
                "The task failed",
                Fakes.clock().instant()));

        assertThat(service.list(member.id(), workspace, issue.id())).singleElement().satisfies(c -> {
            assertThat(c.author().name()).isEqualTo("System");
            assertThat(c.author().id()).isNull();
        });
    }

    @Test
    void rejectsABlankComment() {
        assertThatThrownBy(() -> service.add(member.id(), workspace, issue.id(), "  \n "))
                .isInstanceOf(ValidationException.class);

        assertThat(comments.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void anOutsiderCanNeitherReadNorWrite() {
        service.add(member.id(), workspace, issue.id(), "secret plans");
        published.clear();

        assertThatThrownBy(() -> service.add(outsider.id(), workspace, issue.id(), "hello"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(outsider.id(), workspace, issue.id())).isInstanceOf(NotFoundException.class);

        assertThat(comments.stored).hasSize(1);
        assertThat(published).isEmpty();
    }

    @Test
    void anIssueOfAnotherWorkspaceCannotBeCommentedOn() {
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        IssueView foreign = issueService.create(member.id(), otherWorkspace, new CreateIssue(projects.defaultIn(otherWorkspace), "Elsewhere", null, null, null, null));

        assertThatThrownBy(() -> service.add(member.id(), workspace, foreign.id(), "wrong place"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(member.id(), workspace, foreign.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void commentingOnAMissingIssueIsNotFound() {
        assertThatThrownBy(() -> service.add(member.id(), workspace, UUID.randomUUID(), "hello"))
                .isInstanceOf(NotFoundException.class);
    }
}
