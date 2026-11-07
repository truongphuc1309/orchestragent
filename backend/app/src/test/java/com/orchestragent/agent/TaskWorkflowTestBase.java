package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The world the task tests live in: a workspace with an owner, two members and an outsider, one member with a
 * daemon (an API token and a registered runtime), an agent on that runtime, and helpers that play the part of
 * the daemon with plain HTTP calls. Events between contexts are handled on their own threads after the commit,
 * so what they cause is awaited, never expected at once.
 */
public abstract class TaskWorkflowTestBase extends IntegrationTest {

    protected static final Duration PATIENCE = Duration.ofSeconds(10);

    protected Cookie olivia;
    protected Cookie mia;
    protected Cookie max;
    protected Cookie outsider;
    protected String miaId;
    protected String maxId;
    protected String miaToken;
    protected String maxToken;
    protected String workspaceId;
    protected String issuesBase;
    protected String agentsBase;
    protected String tasksBase;
    protected String runtimeId;
    protected String agentId;

    protected void setUpWorld() throws Exception {
        String miaEmail = uniqueEmail();
        String maxEmail = uniqueEmail();
        olivia = login(uniqueEmail());
        mia = login(miaEmail);
        max = login(maxEmail);
        outsider = login(uniqueEmail());
        miaId = userId(mia);
        maxId = userId(max);

        workspaceId = JsonPath.read(
                mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Work " + UUID.randomUUID().toString().substring(0, 8) + "\"}")
                                .cookie(olivia).with(xsrf()))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
        join(mia, miaEmail);
        join(max, maxEmail);
        issuesBase = "/api/workspaces/" + workspaceId + "/issues";
        agentsBase = "/api/workspaces/" + workspaceId + "/agents";
        tasksBase = "/api/workspaces/" + workspaceId + "/tasks";

        miaToken = createToken(mia);
        maxToken = createToken(max);
        runtimeId = registerRuntime(miaToken, "mia-pc");
        agentId = createAgent(mia, "Builder " + shortId(), runtimeId);
    }

    // ---- people and tokens -------------------------------------------------------------------------------

    protected String userId(Cookie cookie) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/me").cookie(cookie)).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void join(Cookie person, String email) throws Exception {
        String invitation = mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + email + "\",\"role\":\"member\"}").cookie(olivia).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/invitations/" + JsonPath.read(invitation, "$.id") + "/accept").cookie(person).with(xsrf()))
                .andExpect(status().isOk());
    }

    protected String createToken(Cookie session) throws Exception {
        return JsonPath.read(
                mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"daemon\"}").cookie(session).with(xsrf()))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.secret");
    }

    protected static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- the daemon --------------------------------------------------------------------------------------

    protected MockHttpServletRequestBuilder daemon(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected String registerRuntime(String token, String daemonId) throws Exception {
        String body = mvc.perform(daemon(json(post("/api/daemon/register"),
                        "{\"daemonId\":\"" + daemonId + "-" + shortId() + "\",\"deviceName\":\"Device " + daemonId
                                + "\",\"workspaceId\":\"" + workspaceId + "\",\"providers\":[{\"provider\":\"mock\",\"version\":\"1.0\"}]}"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.runtimes[0].id");
    }

    /** The daemon asks for work and gets the raw response. */
    protected String claim(String token, int max, String... runtimes) throws Exception {
        String ids = String.join("\",\"", runtimes);
        return mvc.perform(daemon(json(post("/api/daemon/tasks/claim"), "{\"runtimeIds\":[\"" + ids + "\"],\"maxTasks\":" + max + "}"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected List<String> claimedTaskIds(String claimResponse) {
        return JsonPath.read(claimResponse, "$.tasks[*].taskId");
    }

    protected String start(String token, String taskId) throws Exception {
        return statusOf(mvc.perform(daemon(post("/api/daemon/tasks/" + taskId + "/start"), token))
                .andExpect(status().isOk()));
    }

    protected void sendMessages(String token, String taskId, String batchJson) throws Exception {
        mvc.perform(daemon(json(post("/api/daemon/tasks/" + taskId + "/messages"), "{\"messages\":" + batchJson + "}"), token))
                .andExpect(status().isOk());
    }

    protected String complete(String token, String taskId, String output, String sessionId) throws Exception {
        return statusOf(mvc.perform(daemon(json(post("/api/daemon/tasks/" + taskId + "/complete"),
                        "{\"output\":\"" + output + "\",\"sessionId\":" + quoted(sessionId) + ",\"workDir\":\"/work/" + taskId
                                + "\",\"usage\":{\"inputTokens\":100,\"outputTokens\":40,\"costUsd\":0.0123}}"), token))
                .andExpect(status().isOk()));
    }

    protected String fail(String token, String taskId, String error, String reason) throws Exception {
        return statusOf(mvc.perform(daemon(json(post("/api/daemon/tasks/" + taskId + "/fail"),
                        "{\"error\":\"" + error + "\",\"failureReason\":\"" + reason + "\"}"), token))
                .andExpect(status().isOk()));
    }

    protected String taskStatusFor(String token, String taskId) throws Exception {
        return statusOf(mvc.perform(daemon(get("/api/daemon/tasks/" + taskId + "/status"), token)).andExpect(status().isOk()));
    }

    private static String statusOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.status");
    }

    private static String quoted(String text) {
        return text == null ? "null" : "\"" + text + "\"";
    }

    // ---- the web app -------------------------------------------------------------------------------------

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    protected String createAgent(Cookie by, String name, String runtime) throws Exception {
        return JsonPath.read(
                mvc.perform(as(json(post(agentsBase), "{\"name\":\"" + name + "\",\"runtimeId\":\"" + runtime + "\"}"), by))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    protected String createIssue(String title) throws Exception {
        return createIssue(title, "todo");
    }

    protected String createIssue(String title, String statusCode) throws Exception {
        return JsonPath.read(
                mvc.perform(as(json(post(issuesBase), "{\"title\":\"" + title + "\",\"description\":\"Details of " + title + "\",\"status\":\"" + statusCode + "\"}"), mia))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    protected void assign(String issueId, String agent) throws Exception {
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"),
                        "{\"assignee\":{\"type\":\"agent\",\"id\":\"" + agent + "\"}}"), mia))
                .andExpect(status().isOk());
    }

    protected void unassign(String issueId) throws Exception {
        mvc.perform(as(json(put(issuesBase + "/" + issueId + "/assignee"), "{\"assignee\":null}"), mia))
                .andExpect(status().isOk());
    }

    protected void comment(Cookie by, String issueId, String body) throws Exception {
        mvc.perform(as(json(post(issuesBase + "/" + issueId + "/comments"), "{\"body\":\"" + body + "\"}"), by))
                .andExpect(status().isCreated());
    }

    protected String issueStatus(String issueId) throws Exception {
        return JsonPath.read(
                mvc.perform(get(issuesBase + "/" + issueId).cookie(mia)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.status");
    }

    protected String tasksBody(String issueId) throws Exception {
        return mvc.perform(get(tasksBase + "?issueId=" + issueId).cookie(mia)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Task IDs of an issue, newest first. */
    protected List<String> taskIds(String issueId) throws Exception {
        return JsonPath.read(tasksBody(issueId), "$[*].id");
    }

    protected List<String> taskStatuses(String issueId) throws Exception {
        return JsonPath.read(tasksBody(issueId), "$[*].status");
    }

    protected List<String> commentBodies(String issueId) throws Exception {
        return JsonPath.read(
                mvc.perform(get(issuesBase + "/" + issueId + "/comments").cookie(mia)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$[*].body");
    }

    protected List<String> commentAuthorTypes(String issueId) throws Exception {
        return JsonPath.read(
                mvc.perform(get(issuesBase + "/" + issueId + "/comments").cookie(mia)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$[*].author.type");
    }

    // ---- waiting for what happens on other threads -------------------------------------------------------

    protected void awaitTaskCount(String issueId, int count) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(taskIds(issueId)).hasSize(count));
    }

    /** Waits until the newest task of the issue has this status, and returns its ID. */
    protected String awaitNewestTask(String issueId, String statusCode) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(taskStatuses(issueId)).isNotEmpty().first().isEqualTo(statusCode));
        try {
            return taskIds(issueId).getFirst();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void awaitIssueStatus(String issueId, String statusCode) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(issueStatus(issueId)).isEqualTo(statusCode));
    }

    /** Asserts that something does not happen: the state has to hold for a moment, not just right now. */
    protected void assertStaysTrue(Runnable check) {
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(4)).untilAsserted(check::run);
    }
}
