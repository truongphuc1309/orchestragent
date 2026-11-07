package com.orchestragent.inbox.application;

import com.orchestragent.agent.contract.AgentDirectory;
import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationId;
import com.orchestragent.inbox.domain.NotificationRepository;
import com.orchestragent.inbox.domain.NotificationType;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.shared.id.UuidV7;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who gets told what. Called by the listeners of other contexts' events; every rule is safe to run twice for the
 * same event (the event registry may deliver it again): a notification remembers its event, and an event makes at
 * most one notification per person.
 *
 * <ul>
 *   <li>An issue is assigned to a person by someone else: that person.</li>
 *   <li>An agent finished an issue (it waits for review) or failed on it: the person who created the issue.</li>
 *   <li>An issue is deleted: its notifications go away.</li>
 * </ul>
 *
 * Nobody is told about their own action, and people who are no longer members are not told anything.
 */
@Service
public class NotificationRules {

    static final String SYSTEM_NAME = "Orchestragent";
    static final String UNKNOWN_NAME = "Someone";

    private final NotificationRepository notifications;
    private final IssueDirectory issues;
    private final AgentDirectory agents;
    private final UserDirectory users;
    private final WorkspaceAccess access;
    private final InboxEvents events;
    private final Clock clock;

    NotificationRules(
            NotificationRepository notifications,
            IssueDirectory issues,
            AgentDirectory agents,
            UserDirectory users,
            WorkspaceAccess access,
            InboxEvents events,
            Clock clock) {
        this.notifications = notifications;
        this.issues = issues;
        this.agents = agents;
        this.users = users;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void assigneeChanged(IssueAssigneeChanged event) {
        if (event.newAssigneeType() != ActorType.MEMBER || event.newAssigneeId() == null) {
            return;
        }
        UUID recipient = event.newAssigneeId();
        if (event.actorType() == ActorType.MEMBER && recipient.equals(event.actorId())) {
            return;
        }
        String actor = name(event.workspaceId(), event.actorType(), event.actorId());
        issues.find(event.workspaceId(), event.issueId()).ifPresent(issue ->
                notify(issue, recipient, NotificationType.ISSUE_ASSIGNED, actor, null, event.eventId()));
    }

    @Transactional
    public void taskCompleted(AgentTaskCompleted event) {
        String agent = name(event.workspaceId(), ActorType.AGENT, event.agentId());
        issues.find(event.workspaceId(), event.issueId()).ifPresent(issue ->
                creatorOf(issue).ifPresent(creator ->
                        notify(issue, creator, NotificationType.AGENT_COMPLETED, agent, null, event.eventId())));
    }

    @Transactional
    public void taskFailed(AgentTaskFailed event) {
        String agent = name(event.workspaceId(), ActorType.AGENT, event.agentId());
        issues.find(event.workspaceId(), event.issueId()).ifPresent(issue ->
                creatorOf(issue).ifPresent(creator ->
                        notify(issue, creator, NotificationType.AGENT_FAILED, agent, event.failureReason(), event.eventId())));
    }

    @Transactional
    public void issueDeleted(IssueDeleted event) {
        for (UUID recipient : notifications.deleteByIssue(event.workspaceId(), event.issueId())) {
            events.changed(event.workspaceId(), recipient);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private void notify(IssueDetails issue, UUID recipient, NotificationType type, String actor, String detail, UUID eventId) {
        if (!access.isMember(issue.workspaceId(), recipient)) {
            return;
        }
        Notification notification = Notification.create(
                new NotificationId(UuidV7.next()),
                issue.workspaceId(),
                recipient,
                type,
                issue.id(),
                issue.number(),
                issue.title(),
                actor,
                detail,
                eventId,
                clock.instant());
        if (notifications.addOnce(notification)) {
            events.changed(issue.workspaceId(), recipient);
        }
    }

    /** The person who created the issue; an issue an agent created has nobody to tell. */
    private static Optional<UUID> creatorOf(IssueDetails issue) {
        return issue.creatorType() == ActorType.MEMBER ? Optional.ofNullable(issue.creatorId()) : Optional.empty();
    }

    private String name(UUID workspaceId, ActorType type, UUID id) {
        if (type == null || id == null) {
            return UNKNOWN_NAME;
        }
        return switch (type) {
            case MEMBER -> users.findById(id).map(UserView::name).filter(n -> !n.isBlank()).orElse(UNKNOWN_NAME);
            case AGENT -> agents.find(workspaceId, id).map(AgentSummary::name).orElse(UNKNOWN_NAME);
            case SYSTEM -> SYSTEM_NAME;
        };
    }
}
