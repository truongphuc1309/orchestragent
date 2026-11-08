package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.agent.application.RuntimeService;
import com.orchestragent.agent.application.RuntimeService.ProviderInfo;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Issues and agents together: only active agents of the same workspace can be given work. */
@RecordApplicationEvents
class AgentAssigneeApiTest extends IntegrationTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Cookie mia;
    private Cookie max;
    private String miaId;
    private String workspaceId;
    private String agentsBase;
    private String issuesBase;
    private String runtimeId;
    private String agentId;

    @BeforeEach
    void setUp() throws Exception {
        String maxEmail = uniqueEmail();
        mia = login(uniqueEmail());
        max = login(maxEmail);
        miaId = JsonPath.read(mvc.perform(get("/api/me").cookie(mia)).andReturn().getResponse().getContentAsString(), "$.id");
        workspaceId = createWorkspace(mia, "Assign " + UUID.randomUUID().toString().substring(0, 8));
        String invitation = mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + maxEmail + "\",\"role\":\"member\"}").cookie(mia).with(xsrf()))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/invitations/" + JsonPath.read(invitation, "$.id") + "/accept").cookie(max).with(xsrf()))
                .andExpect(status().isOk());
        agentsBase = "/api/workspaces/" + workspaceId + "/agents";
        issuesBase = "/api/workspaces/" + workspaceId + "/issues";
        runtimeId = runtimeIn(workspaceId);
        agentId = createAgent(agentsBase, runtimeId, "Builder");
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private String createWorkspace(Cookie cookie, String name) throws Exception {
        String body = mvc.perform(json(post("/api/workspaces"), "{\"name\":\"" + name + "\"}").cookie(cookie).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** A runtime of Mia's in the workspace, as a daemon would register it. */
    private String runtimeIn(String workspace) {
        return runtimeService
                .register(UUID.fromString(miaId), UUID.fromString(workspace), "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id().toString();
    }

    private String createAgent(String base, String runtime, String name) throws Exception {
        String body = mvc.perform(as(json(post(base),
                        "{\"name\":\"" + name + "\",\"runtimeId\":\"" + runtime + "\"}"), mia))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    private String createIssue(String title) throws Exception {
        String body = mvc.perform(as(json(post(issuesBase),
                        "{\"projectId\":\"" + projectIn(workspaceId, mia) + "\",\"title\":\"" + title + "\"}"), mia))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String agentAssignee(String id) {
        return "{\"assignee\":{\"type\":\"agent\",\"id\":\"" + id + "\"}}";
    }

    // ---- tests -------------------------------------------------------------------------------------------

    @Test
    void anIssueCanBeAssignedToAnAgentByAnyMember() throws Exception {
        String issueId = createIssue("Fix it");

        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(agentId)), max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.type").value("agent"))
                .andExpect(jsonPath("$.assignee.id").value(agentId))
                .andExpect(jsonPath("$.assignee.name").value("Builder"));
        mvc.perform(get(issuesBase + "/" + issueId).cookie(mia))
                .andExpect(jsonPath("$.assignee.type").value("agent"))
                .andExpect(jsonPath("$.assignee.name").value("Builder"));
    }

    @Test
    void anIssueCanBeCreatedForAnAgent() throws Exception {
        mvc.perform(as(json(post(issuesBase), "{\"projectId\":\"" + projectIn(workspaceId, mia) + "\",\"title\":\"For the agent\"," + agentAssignee(agentId).substring(1)), mia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.type").value("agent"))
                .andExpect(jsonPath("$.assignee.name").value("Builder"));
    }

    @Test
    void assigningToAnAgentPublishesAnAssigneeChangedEventOfTypeAgent() throws Exception {
        String issueId = createIssue("Events");

        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(agentId)), mia))
                .andExpect(status().isOk());

        assertThat(applicationEvents.stream(IssueAssigneeChanged.class))
                .anySatisfy(event -> {
                    assertThat(event.issueId()).hasToString(issueId);
                    assertThat(event.newAssigneeType()).isEqualTo(ActorType.AGENT);
                    assertThat(event.newAssigneeId()).hasToString(agentId);
                    assertThat(event.previousAssigneeType()).isNull();
                });
    }

    @Test
    void anArchivedAgentCannotBeAssignedButComesBackWhenRestored() throws Exception {
        String issueId = createIssue("Archived");
        mvc.perform(as(post(agentsBase + "/" + agentId + "/archive"), mia)).andExpect(status().isOk());

        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(agentId)), mia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("issue.invalid_assignee"));
        mvc.perform(as(json(post(issuesBase), "{\"projectId\":\"" + projectIn(workspaceId, mia) + "\",\"title\":\"x\"," + agentAssignee(agentId).substring(1)), mia))
                .andExpect(status().isBadRequest());
        mvc.perform(get(issuesBase + "/" + issueId).cookie(mia)).andExpect(jsonPath("$.assignee").doesNotExist());

        mvc.perform(as(post(agentsBase + "/" + agentId + "/restore"), mia)).andExpect(status().isOk());
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(agentId)), mia))
                .andExpect(status().isOk());
    }

    @Test
    void anIssueKeepsItsAgentAndTheAgentsNameAfterTheAgentIsArchived() throws Exception {
        String issueId = createIssue("Old work");
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(agentId)), mia))
                .andExpect(status().isOk());

        mvc.perform(as(post(agentsBase + "/" + agentId + "/archive"), mia)).andExpect(status().isOk());

        mvc.perform(get(issuesBase + "/" + issueId).cookie(mia))
                .andExpect(jsonPath("$.assignee.type").value("agent"))
                .andExpect(jsonPath("$.assignee.name").value("Builder"));
        // Other edits to that issue still work, and so does unassigning it.
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), "{\"assignee\":null}"), mia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    void anAgentOfAnotherWorkspaceCannotBeAssigned() throws Exception {
        String other = createWorkspace(mia, "Other " + UUID.randomUUID().toString().substring(0, 8));
        String foreignAgent =
                createAgent("/api/workspaces/" + other + "/agents", runtimeIn(other), "Foreign");
        String issueId = createIssue("Mine");

        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(foreignAgent)), mia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("issue.invalid_assignee"));
    }

    @Test
    void anUnknownAgentCannotBeAssigned() throws Exception {
        String issueId = createIssue("Mine");

        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), agentAssignee(UUID.randomUUID().toString())), mia))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuesCanBeFilteredByAgent() throws Exception {
        String mine = createIssue("For the agent");
        createIssue("For nobody");
        mvc.perform(as(json(put(issuesBase + "/" + mine + "/assignee"), agentAssignee(agentId)), mia))
                .andExpect(status().isOk());

        String body = mvc.perform(get(issuesBase + "?assignee=" + agentId).cookie(mia)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].title")).containsExactly("For the agent");
    }
}
