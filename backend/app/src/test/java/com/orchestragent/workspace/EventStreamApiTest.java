package com.orchestragent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.platform.sse.SseHub;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The live event stream (Server-Sent Events) end to end: who may listen, which events arrive, and that a
 * workspace never hears about another one. The listeners run on their own thread after the commit, so the
 * tests wait for events instead of expecting them at once.
 */
class EventStreamApiTest extends IntegrationTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    @Autowired
    private SseHub hub;

    private Cookie owner;
    private Cookie member;
    private Cookie outsider;
    private String ownerId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        String memberEmail = uniqueEmail();
        owner = login(uniqueEmail());
        member = login(memberEmail);
        outsider = login(uniqueEmail());
        ownerId = JsonPath.read(
                mvc.perform(get("/api/me").cookie(owner)).andReturn().getResponse().getContentAsString(), "$.id");
        workspaceId = createWorkspace("Live " + UUID.randomUUID().toString().substring(0, 8));
        String invitation = mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + memberEmail + "\",\"role\":\"member\"}").cookie(owner).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(invitation, "$.id");
        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(member).with(xsrf()))
                .andExpect(status().isOk());
        projectAnnounced(workspaceId);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    /**
     * Creates the workspace's project for the issues of a test and waits until its own live event has been
     * handled, so it cannot turn up in a stream the test opens afterwards.
     */
    private void projectAnnounced(String workspace) throws Exception {
        String projectId = projectIn(workspace, owner);
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(jdbc
                        .sql("select count(*) from event_publication where serialized_event like :id")
                        .param("id", "%" + projectId + "%")
                        .query(Long.class)
                        .single())
                .isZero());
    }

    private String createWorkspace(String name) throws Exception {
        String body = mvc.perform(json(post("/api/workspaces"), "{\"name\":\"" + name + "\"}").cookie(owner).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    /** Opens the stream the way a browser does and returns the open connection. */
    private MvcResult listen(String workspace, Cookie cookie) throws Exception {
        return mvc.perform(get("/api/workspaces/" + workspace + "/events").cookie(cookie).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private String createIssue(String workspace, Cookie by, String title) throws Exception {
        String body = mvc.perform(as(json(post("/api/workspaces/" + workspace + "/issues"),
                        "{\"projectId\":\"" + projectIn(workspace, by) + "\",\"title\":\"" + title + "\"}"), by))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** One received event: its name and the raw JSON data. */
    private record Received(String name, String data) {

        String field(String path) {
            Object value = JsonPath.read(data, path);
            return String.valueOf(value);
        }
    }

    /**
     * The events of the stream, without the keep-alive heartbeats: the hub sends those on a timer to every open
     * stream, so they can show up in any test at any moment and say nothing about what the test is checking.
     */
    private static List<Received> received(MvcResult stream) throws Exception {
        return everything(stream).stream().filter(event -> !event.name().equals("heartbeat")).toList();
    }

    private static List<Received> everything(MvcResult stream) throws Exception {
        List<Received> events = new ArrayList<>();
        for (String block : stream.getResponse().getContentAsString().split("\n\n")) {
            String name = null;
            String data = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length());
                }
            }
            if (name != null && data != null) {
                events.add(new Received(name, data));
            }
        }
        return events;
    }

    private static List<String> names(MvcResult stream) throws Exception {
        return received(stream).stream().map(Received::name).toList();
    }

    private static void awaitNames(MvcResult stream, String... expected) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(names(stream)).containsExactlyInAnyOrder(expected));
    }

    // ---- who may listen ----------------------------------------------------------------------------------

    @Test
    void aMemberConnectsAndGetsAnImmediateSignThatTheStreamIsOpen() throws Exception {
        MvcResult stream = listen(workspaceId, member);

        assertThat(stream.getResponse().getContentType()).startsWith("text/event-stream");
        await().atMost(PATIENCE).untilAsserted(
                () -> assertThat(stream.getResponse().getContentAsString()).contains(":connected"));
    }

    @Test
    void idleConnectionsGetAHeartbeatEventThatClientsCanSee() throws Exception {
        MvcResult stream = listen(workspaceId, member);

        hub.sendHeartbeats();

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(everything(stream)).extracting(Received::name).contains("heartbeat"));
    }

    @Test
    void anOutsiderGetsNotFoundLikeOnEveryOtherWorkspaceEndpoint() throws Exception {
        mvc.perform(get("/api/workspaces/" + workspaceId + "/events").cookie(outsider).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWorkspaceThatDoesNotExistLooksTheSame() throws Exception {
        mvc.perform(get("/api/workspaces/" + UUID.randomUUID() + "/events").cookie(owner).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousVisitorGetsUnauthorized() throws Exception {
        mvc.perform(get("/api/workspaces/" + workspaceId + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    // ---- what arrives ------------------------------------------------------------------------------------

    @Test
    void everyChangeToAnIssueIsAnnouncedToTheOtherTab() throws Exception {
        MvcResult stream = listen(workspaceId, member);
        String base = "/api/workspaces/" + workspaceId + "/issues";

        String issueId = createIssue(workspaceId, owner, "Live issue");
        mvc.perform(as(json(patch(base + "/" + issueId), "{\"title\":\"Renamed\",\"priority\":\"high\"}"), owner))
                .andExpect(status().isOk());
        mvc.perform(as(json(put(base + "/" + issueId + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + ownerId + "\"}}"), owner))
                .andExpect(status().isOk());
        mvc.perform(as(json(post(base + "/" + issueId + "/move"), "{\"status\":\"done\"}"), owner))
                .andExpect(status().isOk());
        mvc.perform(as(json(post(base + "/" + issueId + "/comments"), "{\"body\":\"hello\"}"), owner))
                .andExpect(status().isCreated());
        mvc.perform(as(delete(base + "/" + issueId), owner)).andExpect(status().isNoContent());

        awaitNames(stream,
                "issue.created", "issue.updated", "issue.updated", "issue.updated",
                "comment.created", "issue.deleted");
        List<Received> events = received(stream);
        assertThat(events).allSatisfy(event -> assertThat(event.field("$.issueId")).isEqualTo(issueId));
        assertThat(events.stream().filter(e -> e.name().equals("issue.created")).findFirst().orElseThrow().field("$.key"))
                .isEqualTo("WEB-1");
        List<String> changes = events.stream()
                .filter(e -> e.name().equals("issue.updated"))
                .map(e -> e.field("$.changedFields"))
                .sorted()
                .toList();
        assertThat(changes).anySatisfy(fields -> assertThat(fields).contains("title").contains("priority"));
        assertThat(changes).anySatisfy(fields -> assertThat(fields).contains("assignee"));
        assertThat(changes).anySatisfy(fields -> assertThat(fields).contains("status").contains("position"));
        assertThat(events.stream().filter(e -> e.name().equals("comment.created")).findFirst().orElseThrow().field("$.commentId"))
                .isNotBlank();
    }

    @Test
    void everyOpenTabOfEveryMemberGetsTheEvent() throws Exception {
        MvcResult first = listen(workspaceId, member);
        MvcResult second = listen(workspaceId, member);
        MvcResult ownersTab = listen(workspaceId, owner);

        createIssue(workspaceId, owner, "For everyone");

        awaitNames(first, "issue.created");
        awaitNames(second, "issue.created");
        awaitNames(ownersTab, "issue.created");
    }

    @Test
    void aWorkspaceNeverHearsAboutAnother() throws Exception {
        String otherWorkspace = createWorkspace("Other " + UUID.randomUUID().toString().substring(0, 8));
        projectAnnounced(otherWorkspace);
        MvcResult here = listen(workspaceId, owner);
        MvcResult there = listen(otherWorkspace, owner);

        String issueHere = createIssue(workspaceId, owner, "Only here");
        awaitNames(here, "issue.created");
        String issueThere = createIssue(otherWorkspace, owner, "Only there");
        awaitNames(there, "issue.created");

        assertThat(received(here)).extracting(e -> e.field("$.issueId")).containsExactly(issueHere);
        assertThat(received(there)).extracting(e -> e.field("$.issueId")).containsExactly(issueThere);
    }

    @Test
    void aFailedChangeAnnouncesNothing() throws Exception {
        MvcResult stream = listen(workspaceId, member);
        String base = "/api/workspaces/" + workspaceId + "/issues";

        mvc.perform(as(json(post(base), "{\"title\":\"   \"}"), owner)).andExpect(status().isBadRequest());
        String issueId = createIssue(workspaceId, owner, "The only one");

        awaitNames(stream, "issue.created");
        assertThat(received(stream)).extracting(e -> e.field("$.issueId")).containsExactly(issueId);
    }

    @Test
    void projectChangesAreAnnounced() throws Exception {
        MvcResult stream = listen(workspaceId, member);
        String base = "/api/workspaces/" + workspaceId + "/projects";

        String api = createProject(workspaceId, owner, "API");
        mvc.perform(as(json(patch(base + "/" + api), "{\"name\":\"Backend\"}"), owner)).andExpect(status().isOk());
        mvc.perform(as(post(base + "/" + api + "/archive"), owner)).andExpect(status().isOk());
        mvc.perform(as(post(base + "/" + api + "/restore"), owner)).andExpect(status().isOk());

        awaitNames(stream, "project.updated", "project.updated", "project.updated", "project.updated");
        assertThat(received(stream)).allSatisfy(event -> assertThat(event.field("$.projectId")).isEqualTo(api));
    }

    @Test
    void handledEventsDoNotPileUpInTheDatabase() throws Exception {
        MvcResult stream = listen(workspaceId, member);
        String issueId = createIssue(workspaceId, owner, "Tidy");
        awaitNames(stream, "issue.created");

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(jdbc
                        .sql("select count(*) from event_publication where serialized_event like :id")
                        .param("id", "%" + issueId + "%")
                        .query(Long.class)
                        .single())
                .isZero());
    }
}
