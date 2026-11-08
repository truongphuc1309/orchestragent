package com.orchestragent.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.inbox.application.InboxFakes.FakeAccess;
import com.orchestragent.inbox.application.InboxFakes.FakeAgents;
import com.orchestragent.inbox.application.InboxFakes.FakeIssues;
import com.orchestragent.inbox.application.InboxFakes.FakeUsers;
import com.orchestragent.inbox.application.InboxFakes.InMemoryNotifications;
import com.orchestragent.inbox.application.InboxFakes.MutableClock;
import com.orchestragent.inbox.application.InboxFakes.Published;
import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationType;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueDetails;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRulesTest {

    private final UUID workspace = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final FakeIssues issues = new FakeIssues();
    private final FakeAgents agents = new FakeAgents();
    private final FakeUsers users = new FakeUsers();
    private final FakeAccess access = new FakeAccess();
    private final Published published = new Published();
    private final NotificationRules rules = new NotificationRules(
            notifications, issues, agents, users, access, new InboxEvents(published, clock), clock);

    private final UUID olivia = users.add("Olivia");
    private final UUID mia = users.add("Mia");
    private final AgentSummary builder = agents.add(workspace, "Builder");
    private final IssueDetails issue = issues.add(workspace, 12, ActorType.MEMBER, mia);

    NotificationRulesTest() {
        access.join(workspace, olivia);
        access.join(workspace, mia);
    }

    private IssueAssigneeChanged assigned(UUID eventId, ActorType newType, UUID newId, ActorType actorType, UUID actorId) {
        return new IssueAssigneeChanged(eventId, Instant.now(), workspace, issue.id(), null, null, newType, newId, actorType, actorId);
    }

    private AgentTaskCompleted completed(UUID eventId) {
        return new AgentTaskCompleted(eventId, Instant.now(), workspace, issue.id(), UUID.randomUUID(), builder.id(), "Done");
    }

    // ---- assignment --------------------------------------------------------------------------------------

    @Test
    void aPersonAssignedBySomeoneElseIsTold() {
        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.MEMBER, mia, ActorType.MEMBER, olivia));

        assertThat(notifications.all).singleElement().satisfies(n -> {
            assertThat(n.recipientId()).isEqualTo(mia);
            assertThat(n.type()).isEqualTo(NotificationType.ISSUE_ASSIGNED);
            assertThat(n.actorName()).isEqualTo("Olivia");
            assertThat(n.issueKey()).isEqualTo("WEB-12");
            assertThat(n.issueTitle()).isEqualTo("Issue 12");
            assertThat(n.isRead()).isFalse();
        });
        assertThat(published.recipients()).containsExactly(mia);
    }

    @Test
    void assigningYourselfTellsNobody() {
        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.MEMBER, mia, ActorType.MEMBER, mia));

        assertThat(notifications.all).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void assigningAnAgentOrNobodyTellsNobody() {
        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.AGENT, builder.id(), ActorType.MEMBER, olivia));
        rules.assigneeChanged(assigned(UUID.randomUUID(), null, null, ActorType.MEMBER, olivia));

        assertThat(notifications.all).isEmpty();
    }

    @Test
    void aPersonWhoLeftIsNotTold() {
        access.leave(workspace, mia);

        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.MEMBER, mia, ActorType.MEMBER, olivia));

        assertThat(notifications.all).isEmpty();
    }

    @Test
    void anIssueThatIsGoneTellsNobody() {
        issues.byId.clear();

        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.MEMBER, mia, ActorType.MEMBER, olivia));
        rules.taskCompleted(completed(UUID.randomUUID()));

        assertThat(notifications.all).isEmpty();
    }

    // ---- agents ------------------------------------------------------------------------------------------

    @Test
    void theCreatorIsToldWhenAnAgentFinishes() {
        rules.taskCompleted(completed(UUID.randomUUID()));

        assertThat(notifications.all).singleElement().satisfies(n -> {
            assertThat(n.recipientId()).isEqualTo(mia);
            assertThat(n.type()).isEqualTo(NotificationType.AGENT_COMPLETED);
            assertThat(n.actorName()).isEqualTo("Builder");
        });
        assertThat(published.recipients()).containsExactly(mia);
    }

    @Test
    void theCreatorIsToldWhenAnAgentFailsWithTheReason() {
        rules.taskFailed(new AgentTaskFailed(UUID.randomUUID(), Instant.now(), workspace, issue.id(), UUID.randomUUID(),
                builder.id(), "timeout", "Took too long"));

        assertThat(notifications.all).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.AGENT_FAILED);
            assertThat(n.detail()).isEqualTo("timeout");
            assertThat(n.recipientId()).isEqualTo(mia);
        });
    }

    @Test
    void anIssueAnAgentCreatedHasNobodyToTell() {
        IssueDetails byAgent = issues.add(workspace, 13, ActorType.AGENT, builder.id());
        // The creator's type decides, not the membership check that comes after it.
        access.join(workspace, builder.id());

        rules.taskCompleted(new AgentTaskCompleted(UUID.randomUUID(), Instant.now(), workspace, byAgent.id(), UUID.randomUUID(),
                builder.id(), "Done"));

        assertThat(notifications.all).isEmpty();
    }

    @Test
    void anUnknownAgentStillMakesANotification() {
        rules.taskCompleted(new AgentTaskCompleted(UUID.randomUUID(), Instant.now(), workspace, issue.id(), UUID.randomUUID(),
                UUID.randomUUID(), "Done"));

        assertThat(notifications.all).singleElement().satisfies(n -> assertThat(n.actorName()).isEqualTo(NotificationRules.UNKNOWN_NAME));
    }

    // ---- delivered twice ---------------------------------------------------------------------------------

    @Test
    void anEventDeliveredTwiceMakesOneNotificationAndOneUpdate() {
        UUID assignment = UUID.randomUUID();
        UUID completion = UUID.randomUUID();

        rules.assigneeChanged(assigned(assignment, ActorType.MEMBER, mia, ActorType.MEMBER, olivia));
        rules.assigneeChanged(assigned(assignment, ActorType.MEMBER, mia, ActorType.MEMBER, olivia));
        rules.taskCompleted(completed(completion));
        rules.taskCompleted(completed(completion));

        assertThat(notifications.all).extracting(Notification::sourceEventId).containsExactly(assignment, completion);
        assertThat(published).hasSize(2);
    }

    // ---- deletion ----------------------------------------------------------------------------------------

    @Test
    void deletingAnIssueRemovesItsNotificationsAndTellsTheirOwners() {
        rules.assigneeChanged(assigned(UUID.randomUUID(), ActorType.MEMBER, mia, ActorType.MEMBER, olivia));
        IssueDetails other = issues.add(workspace, 14, ActorType.MEMBER, olivia);
        rules.taskCompleted(new AgentTaskCompleted(UUID.randomUUID(), Instant.now(), workspace, other.id(), UUID.randomUUID(),
                builder.id(), "Done"));
        published.clear();

        IssueDeleted deleted = new IssueDeleted(UUID.randomUUID(), Instant.now(), workspace, issue.id(), "WEB-12");
        rules.issueDeleted(deleted);
        rules.issueDeleted(deleted);

        assertThat(notifications.all).singleElement().satisfies(n -> assertThat(n.issueId()).isEqualTo(other.id()));
        assertThat(published.recipients()).containsExactly(mia);
    }
}
