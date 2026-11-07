package com.orchestragent.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.agent.TaskWorkflowTestBase;
import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.inbox.application.NotificationRules;
import com.orchestragent.shared.id.UuidV7;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The inbox end to end: real events from the issue and agent contexts make notifications for the right person,
 * through the event registry, and the API only ever shows a person their own.
 */
class InboxApiTest extends TaskWorkflowTestBase {

    @Autowired
    private NotificationRules rules;

    private String inboxBase;

    @BeforeEach
    void setUp() throws Exception {
        setUpWorld();
        inboxBase = "/api/workspaces/" + workspaceId + "/inbox";
    }

    private String inbox(Cookie who) throws Exception {
        return mvc.perform(get(inboxBase).cookie(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int unread(Cookie who) throws Exception {
        return JsonPath.read(mvc.perform(get(inboxBase + "/unread-count").cookie(who))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.unreadCount");
    }

    private List<String> types(Cookie who) throws Exception {
        return JsonPath.read(inbox(who), "$.notifications[*].type");
    }

    private void assignTo(Cookie by, String issueId, String memberId) throws Exception {
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + memberId + "\"}}"), by))
                .andExpect(status().isOk());
    }

    private String runAgentOn(String issueId) throws Exception {
        assign(issueId, agentId);
        awaitTaskCount(issueId, 1);
        String taskId = claimedTaskIds(claim(miaToken, 5, runtimeId)).getFirst();
        start(miaToken, taskId);
        return taskId;
    }

    @Test
    void someoneAssignedByAnotherPersonIsToldAndOnlyThem() throws Exception {
        String issueId = createIssue("Write the docs");

        assignTo(max, issueId, miaId);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(unread(mia)).isEqualTo(1));
        String body = inbox(mia);
        assertThat(JsonPath.<String>read(body, "$.notifications[0].type")).isEqualTo("issue_assigned");
        assertThat(JsonPath.<String>read(body, "$.notifications[0].issueTitle")).isEqualTo("Write the docs");
        assertThat(JsonPath.<String>read(body, "$.notifications[0].issueId")).isEqualTo(issueId);
        assertThat(JsonPath.<String>read(body, "$.notifications[0].actorName")).isNotBlank();
        assertThat(JsonPath.<Object>read(body, "$.notifications[0].readAt")).isNull();
        assertStaysTrue(() -> {
            try {
                assertThat(types(max)).isEmpty();
                assertThat(types(olivia)).isEmpty();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void assigningYourselfTellsNobody() throws Exception {
        String issueId = createIssue("Mine");

        assignTo(mia, issueId, miaId);

        assertStaysTrue(() -> {
            try {
                assertThat(unread(mia)).isZero();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void theCreatorIsToldWhenTheAgentFinishesAndWhenItFails() throws Exception {
        String done = createIssue("Finish me");
        complete(miaToken, runAgentOn(done), "All done", "s-1");
        awaitIssueStatus(done, "in_review");

        String broken = createIssue("Break me");
        fail(miaToken, runAgentOn(broken), "It broke", "timeout");

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(types(mia)).containsExactly("agent_failed", "agent_completed"));
        String body = inbox(mia);
        assertThat(JsonPath.<String>read(body, "$.notifications[0].detail")).isEqualTo("timeout");
        assertThat(JsonPath.<String>read(body, "$.notifications[1].actorName")).startsWith("Builder");
        assertThat(types(max)).isEmpty();
    }

    @Test
    void readingOneOrAllCountsDownAndOnlyForMe() throws Exception {
        assignTo(max, createIssue("One"), miaId);
        assignTo(max, createIssue("Two"), miaId);
        assignTo(mia, createIssue("For Max"), maxId);
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(unread(mia)).isEqualTo(2));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(unread(max)).isEqualTo(1));
        String miasFirst = JsonPath.read(inbox(mia), "$.notifications[0].id");
        String maxsOnly = JsonPath.read(inbox(max), "$.notifications[0].id");

        mvc.perform(as(post(inboxBase + "/" + miasFirst + "/read"), mia))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.readAt")).isNotNull());
        assertThat(unread(mia)).isEqualTo(1);

        // Another person's notification does not exist for me.
        mvc.perform(as(post(inboxBase + "/" + maxsOnly + "/read"), mia)).andExpect(status().isNotFound());
        assertThat(unread(max)).isEqualTo(1);

        mvc.perform(as(post(inboxBase + "/read-all"), mia)).andExpect(status().isNoContent());
        assertThat(unread(mia)).isZero();
        assertThat(unread(max)).isEqualTo(1);
    }

    @Test
    void deletingTheIssueRemovesItsNotifications() throws Exception {
        String issueId = createIssue("Short lived");
        assignTo(max, issueId, miaId);
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(unread(mia)).isEqualTo(1));

        mvc.perform(as(delete(issuesBase + "/" + issueId), mia)).andExpect(status().isNoContent());

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(types(mia)).isEmpty());
    }

    @Test
    void anEventDeliveredTwiceMakesOneNotificationInTheDatabase() throws Exception {
        String issueId = createIssue("Twice");
        AgentTaskCompleted event = new AgentTaskCompleted(UuidV7.next(), Instant.now(), UUID.fromString(workspaceId),
                UUID.fromString(issueId), UUID.randomUUID(), UUID.fromString(agentId), "Done");

        rules.taskCompleted(event);
        rules.taskCompleted(event);

        assertThat(jdbc.sql("SELECT count(*) FROM inbox.notifications WHERE source_event_id = :id")
                .param("id", event.eventId()).query(Integer.class).single()).isEqualTo(1);
        assertThat(unread(mia)).isEqualTo(1);
    }

    @Test
    void outsidersHaveNoInboxHere() throws Exception {
        mvc.perform(get(inboxBase).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(inboxBase + "/unread-count").cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(inboxBase + "/read-all"), outsider)).andExpect(status().isNotFound());
        mvc.perform(get(inboxBase)).andExpect(status().isUnauthorized());
    }
}
