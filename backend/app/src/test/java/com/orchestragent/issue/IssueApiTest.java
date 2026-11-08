package com.orchestragent.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueCreated;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The issue REST API end to end: behaviour, workspace isolation and who may do what. */
@RecordApplicationEvents
class IssueApiTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents applicationEvents;

    private Cookie owner;
    private Cookie admin;
    private Cookie member;
    private Cookie colleague;
    private Cookie outsider;
    private String ownerId;
    private String memberId;
    private String colleagueId;
    private String workspaceId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        String ownerEmail = uniqueEmail();
        String adminEmail = uniqueEmail();
        String memberEmail = uniqueEmail();
        String colleagueEmail = uniqueEmail();
        owner = login(ownerEmail);
        admin = login(adminEmail);
        member = login(memberEmail);
        colleague = login(colleagueEmail);
        outsider = login(uniqueEmail());
        ownerId = userId(owner);
        memberId = userId(member);
        colleagueId = userId(colleague);

        workspaceId = createWorkspace(owner, "Issues " + UUID.randomUUID().toString().substring(0, 8));
        join(admin, adminEmail, "admin");
        join(member, memberEmail, "member");
        join(colleague, colleagueEmail, "member");
        base = "/api/workspaces/" + workspaceId + "/issues";
        projectId = createProject(workspaceId, owner, "WEB");
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private String userId(Cookie cookie) throws Exception {
        String body = mvc.perform(get("/api/me").cookie(cookie)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createWorkspace(Cookie cookie, String name) throws Exception {
        String body = mvc.perform(json(post("/api/workspaces"), "{\"name\":\"" + name + "\"}").cookie(cookie).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void join(Cookie person, String email, String role) throws Exception {
        String invitation = mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}").cookie(owner).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(invitation, "$.id");
        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(person).with(xsrf()))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    /** A POST that creates an issue in the test's WEB project (unless the body names a project itself). */
    private MockHttpServletRequestBuilder issueJson(String body) {
        return json(post(base), withProject(body, projectId));
    }

    private static String withProject(String body, String project) {
        if (body.contains("\"projectId\"")) {
            return body;
        }
        String rest = body.substring(1).strip();
        return "{\"projectId\":\"" + project + "\"" + (rest.equals("}") ? "" : ",") + rest;
    }

    private String create(Cookie by, String json) throws Exception {
        return mvc.perform(as(issueJson(json), by))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String createIssue(Cookie by, String title, String status) throws Exception {
        String body = create(by, "{\"title\":\"" + title + "\",\"status\":\"" + status + "\"}");
        return JsonPath.read(body, "$.id");
    }

    private String listBody(String query) throws Exception {
        return mvc.perform(get(base + query).cookie(member)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Titles of one board column from top to bottom, ordered by position as the board does. */
    private List<String> column(String statusCode) throws Exception {
        List<String> titles = JsonPath.read(listBody("?status=" + statusCode), "$.items[*].title");
        List<Double> positions = JsonPath.read(listBody("?status=" + statusCode), "$.items[*].position");
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(positions.get(a), positions.get(b)));
        return order.stream().map(titles::get).toList();
    }

    // ---- creating and reading ----------------------------------------------------------------------------

    @Test
    void createsIssuesWithGrowingNumbersAndDefaults() throws Exception {
        mvc.perform(as(issueJson("{\"title\":\"  First  \"}"), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.key").value("WEB-1"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value("First"))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.priority").value("none"))
                .andExpect(jsonPath("$.assignee").doesNotExist())
                .andExpect(jsonPath("$.creator.type").value("member"))
                .andExpect(jsonPath("$.creator.id").value(memberId));
        mvc.perform(as(issueJson("{\"title\":\"Second\"}"), colleague))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.key").value("WEB-2"));
    }

    /** Numbers used to count per workspace; since step 13 every project counts on its own. */
    @Test
    void numbersStartAtOneInEveryProject() throws Exception {
        create(member, "{\"title\":\"here\"}");
        String api = createProject(workspaceId, member, "API");
        String otherWorkspace = createWorkspace(member, "Other " + UUID.randomUUID().toString().substring(0, 8));
        String otherProject = createProject(otherWorkspace, member, "WEB");

        mvc.perform(as(json(post(base), withProject("{\"title\":\"api\"}", api)), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("API-1"));
        mvc.perform(as(json(post("/api/workspaces/" + otherWorkspace + "/issues"),
                        withProject("{\"title\":\"there\"}", otherProject)), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("WEB-1"));
        mvc.perform(as(issueJson("{\"title\":\"again\"}"), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("WEB-2"));
    }

    @Test
    void anIssueNeedsAnActiveProjectOfTheWorkspace() throws Exception {
        String otherWorkspace = createWorkspace(member, "Other " + UUID.randomUUID().toString().substring(0, 8));
        String foreign = createProject(otherWorkspace, member, "OTH");
        String archived = createProject(workspaceId, member, "OLD");
        mvc.perform(post("/api/workspaces/" + workspaceId + "/projects/" + archived + "/archive").cookie(member).with(xsrf()))
                .andExpect(status().isOk());

        mvc.perform(as(json(post(base), "{\"title\":\"x\"}"), member)).andExpect(status().isBadRequest());
        mvc.perform(as(json(post(base), withProject("{\"title\":\"x\"}", UUID.randomUUID().toString())), member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("issue.invalid_project"));
        mvc.perform(as(json(post(base), withProject("{\"title\":\"x\"}", foreign)), member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("issue.invalid_project"));
        mvc.perform(as(json(post(base), withProject("{\"title\":\"x\"}", archived)), member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project.archived"));
        assertThat(JsonPath.<Integer>read(listBody(""), "$.total")).isZero();
    }

    @Test
    void archivingAProjectKeepsItsIssuesReadable() throws Exception {
        String id = createIssue(member, "Old work", "todo");
        mvc.perform(post("/api/workspaces/" + workspaceId + "/projects/" + projectId + "/archive").cookie(owner).with(xsrf()))
                .andExpect(status().isOk());

        mvc.perform(get(base + "/key/WEB-1").cookie(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(titles("")).containsExactly("Old work");
        mvc.perform(as(issueJson("{\"title\":\"new\"}"), member)).andExpect(status().isConflict());
    }

    @Test
    void parallelCreatesNeverShareANumber() throws Exception {
        int count = 12;
        var postProcessor = xsrf();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String title = "parallel " + i;
                jobs.add(() -> JsonPath.<Integer>read(
                        mvc.perform(issueJson("{\"title\":\"" + title + "\"}").cookie(member).with(postProcessor))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString(),
                        "$.number"));
            }
            Set<Integer> numbers = new TreeSet<>();
            for (Future<Integer> result : pool.invokeAll(jobs)) {
                numbers.add(result.get());
            }
            assertThat(numbers).hasSize(count).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.IntStream.rangeClosed(1, count).boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void validatesTheInput() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"title\":\"   \"}",
                "{\"title\":\"" + "x".repeat(201) + "\"}",
                "{\"title\":\"two\\nlines\"}",
                "{\"title\":\"ok\",\"status\":\"finished\"}",
                "{\"title\":\"ok\",\"priority\":\"critical\"}",
                "{\"title\":\"ok\",\"assignee\":{\"type\":\"robot\",\"id\":\"" + memberId + "\"}}",
                "{\"title\":\"ok\",\"assignee\":{\"type\":\"system\",\"id\":\"" + memberId + "\"}}",
                "{\"title\":\"ok\",\"assignee\":{\"type\":\"member\"}}")) {
            mvc.perform(as(issueJson(body), member)).andExpect(status().isBadRequest());
        }
        assertThat(JsonPath.<Integer>read(listBody(""), "$.total")).isZero();
    }

    @Test
    void assigneesMustBeMembersOfTheWorkspace() throws Exception {
        String outsiderId = userId(outsider);
        mvc.perform(as(issueJson(
                        "{\"title\":\"x\",\"assignee\":{\"type\":\"member\",\"id\":\"" + outsiderId + "\"}}"), member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("issue.invalid_assignee"));
        mvc.perform(as(issueJson(
                        "{\"title\":\"x\",\"assignee\":{\"type\":\"agent\",\"id\":\"" + UUID.randomUUID() + "\"}}"), member))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsWithAnAssigneeAndShowsTheirName() throws Exception {
        mvc.perform(as(issueJson(
                        "{\"title\":\"x\",\"assignee\":{\"type\":\"member\",\"id\":\"" + colleagueId + "\"}}"), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.type").value("member"))
                .andExpect(jsonPath("$.assignee.id").value(colleagueId))
                .andExpect(jsonPath("$.assignee.name").isNotEmpty());
    }

    @Test
    void readsAnIssueByIdAndByKey() throws Exception {
        String id = createIssue(member, "Find me", "todo");

        mvc.perform(get(base + "/" + id).cookie(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Find me"));
        mvc.perform(get(base + "/key/WEB-1").cookie(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.key").value("WEB-1"));
        for (String key : List.of("WEB-99", "web-1", "API-1", "WEB", "1")) {
            mvc.perform(get(base + "/key/" + key).cookie(colleague)).andExpect(status().isNotFound());
        }
        mvc.perform(get(base + "/" + UUID.randomUUID()).cookie(colleague)).andExpect(status().isNotFound());
    }

    @Test
    void theSameNumberInTwoProjectsIsTwoIssues() throws Exception {
        String web = createIssue(member, "Web one", "todo");
        String api = createProject(workspaceId, member, "API");
        String apiIssue = JsonPath.read(create(member, withProject("{\"title\":\"Api one\"}", api)), "$.id");

        mvc.perform(get(base + "/key/API-1").cookie(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apiIssue))
                .andExpect(jsonPath("$.title").value("Api one"));
        mvc.perform(get(base + "/key/WEB-1").cookie(colleague)).andExpect(jsonPath("$.id").value(web));
    }

    @Test
    void publishesIssueCreatedThroughTheEventRegistryPath() throws Exception {
        String id = createIssue(member, "Event", "todo");

        assertThat(applicationEvents.stream(IssueCreated.class))
                .anySatisfy(event -> {
                    assertThat(event.issueId()).hasToString(id);
                    assertThat(event.workspaceId()).hasToString(workspaceId);
                    assertThat(event.projectId()).hasToString(projectId);
                    assertThat(event.key()).isEqualTo("WEB-1");
                });
    }

    // ---- listing and filtering ---------------------------------------------------------------------------

    @Test
    void listsNewestFirstWithATotal() throws Exception {
        createIssue(member, "one", "todo");
        createIssue(member, "two", "todo");

        String body = listBody("");

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].title")).containsExactly("two", "one");
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(2);
    }

    @Test
    void filtersByStatusPriorityAndAssignee() throws Exception {
        create(member, "{\"title\":\"a\",\"status\":\"todo\",\"priority\":\"high\",\"assignee\":{\"type\":\"member\",\"id\":\"" + colleagueId + "\"}}");
        create(member, "{\"title\":\"b\",\"status\":\"done\",\"priority\":\"high\"}");
        create(member, "{\"title\":\"c\",\"status\":\"todo\",\"priority\":\"low\"}");

        assertThat(titles("?status=todo")).containsExactly("c", "a");
        assertThat(titles("?status=todo,done")).containsExactly("c", "b", "a");
        assertThat(titles("?status=todo&status=done")).containsExactly("c", "b", "a");
        assertThat(titles("?priority=high")).containsExactly("b", "a");
        assertThat(titles("?assignee=" + colleagueId)).containsExactly("a");
        assertThat(titles("?unassigned=true")).containsExactly("c", "b");
        assertThat(titles("?assignee=" + colleagueId + "&unassigned=true")).containsExactly("c", "b", "a");
        assertThat(titles("?status=todo&priority=high")).containsExactly("a");
    }

    @Test
    void listsIssuesOfEveryProjectAndFiltersByProject() throws Exception {
        String api = createProject(workspaceId, member, "API");
        String docs = createProject(workspaceId, member, "DOC");
        create(member, "{\"title\":\"web\"}");
        mvc.perform(as(json(post(base), withProject("{\"title\":\"api\"}", api)), member)).andExpect(status().isCreated());
        mvc.perform(as(json(post(base), withProject("{\"title\":\"docs\"}", docs)), member)).andExpect(status().isCreated());

        String all = listBody("");
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].title")).containsExactly("docs", "api", "web");
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].key")).containsExactly("DOC-1", "API-1", "WEB-1");
        assertThat(titles("?project=" + api)).containsExactly("api");
        assertThat(titles("?project=" + api + "," + projectId)).containsExactly("api", "web");
        assertThat(titles("?project=" + UUID.randomUUID())).isEmpty();
        mvc.perform(get(base + "?project=not-a-uuid").cookie(member)).andExpect(status().isBadRequest());
    }

    private List<String> titles(String query) throws Exception {
        return JsonPath.read(listBody(query), "$.items[*].title");
    }

    @Test
    void theLimitCutsTheListButNotTheTotal() throws Exception {
        createIssue(member, "one", "todo");
        createIssue(member, "two", "todo");
        createIssue(member, "three", "todo");

        String body = listBody("?limit=2");

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].title")).containsExactly("three", "two");
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(3);
    }

    @Test
    void unknownFilterValuesAreRejected() throws Exception {
        mvc.perform(get(base + "?status=finished").cookie(member)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "?priority=critical").cookie(member)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "?assignee=not-a-uuid").cookie(member)).andExpect(status().isBadRequest());
    }

    // ---- editing -----------------------------------------------------------------------------------------

    @Test
    void anyMemberCanEditAnIssue() throws Exception {
        String id = createIssue(member, "Old", "todo");

        mvc.perform(as(json(patch(base + "/" + id),
                        "{\"title\":\"New\",\"description\":\"Why\",\"priority\":\"urgent\",\"status\":\"in_progress\"}"), colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.description").value("Why"))
                .andExpect(jsonPath("$.priority").value("urgent"))
                .andExpect(jsonPath("$.status").value("in_progress"));
        mvc.perform(get(base + "/" + id).cookie(member))
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.status").value("in_progress"));
    }

    @Test
    void aPatchLeavesMissingFieldsAlone() throws Exception {
        String id = createIssue(member, "Keep", "todo");

        mvc.perform(as(json(patch(base + "/" + id), "{\"priority\":\"low\"}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Keep"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.priority").value("low"));
    }

    @Test
    void invalidPatchesAreRejectedAndChangeNothing() throws Exception {
        String id = createIssue(member, "Keep", "todo");

        for (String body : List.of(
                "{\"title\":\"\"}", "{\"title\":\"" + "x".repeat(201) + "\"}", "{\"status\":\"finished\"}",
                "{\"priority\":\"critical\"}")) {
            mvc.perform(as(json(patch(base + "/" + id), body), member)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(base + "/" + id).cookie(member)).andExpect(jsonPath("$.title").value("Keep"));
    }

    @Test
    void assignsAndUnassigns() throws Exception {
        String id = createIssue(member, "Assign me", "todo");

        mvc.perform(as(json(put(base + "/" + id + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + colleagueId + "\"}}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(colleagueId));
        mvc.perform(as(json(put(base + "/" + id + "/assignee"), "{\"assignee\":null}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
        mvc.perform(as(json(put(base + "/" + id + "/assignee"), "{}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    void cannotAssignToAnOutsider() throws Exception {
        String id = createIssue(member, "Assign me", "todo");
        String outsiderId = userId(outsider);

        mvc.perform(as(json(put(base + "/" + id + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + outsiderId + "\"}}"), member))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/" + id).cookie(member)).andExpect(jsonPath("$.assignee").doesNotExist());
    }

    // ---- moving ------------------------------------------------------------------------------------------

    @Test
    void dragsAnIssueBetweenTwoOthers() throws Exception {
        String a = createIssue(member, "a", "todo");
        String b = createIssue(member, "b", "todo");
        String c = createIssue(member, "c", "todo");

        mvc.perform(as(json(post(base + "/" + c + "/move"),
                        "{\"status\":\"todo\",\"aboveId\":\"" + a + "\",\"belowId\":\"" + b + "\"}"), colleague))
                .andExpect(status().isOk());

        assertThat(column("todo")).containsExactly("a", "c", "b");
    }

    @Test
    void dragsAnIssueToAnotherColumn() throws Exception {
        String a = createIssue(member, "a", "todo");
        createIssue(member, "x", "in_review");

        mvc.perform(as(json(post(base + "/" + a + "/move"), "{\"status\":\"in_review\"}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_review"));

        assertThat(column("in_review")).containsExactly("x", "a");
        assertThat(column("todo")).isEmpty();
    }

    @Test
    void aStaleDragIsAConflict() throws Exception {
        String a = createIssue(member, "a", "todo");
        String elsewhere = createIssue(member, "e", "done");

        mvc.perform(as(json(post(base + "/" + a + "/move"),
                        "{\"status\":\"todo\",\"aboveId\":\"" + elsewhere + "\"}"), member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("issue.stale_move"));
        mvc.perform(as(json(post(base + "/" + a + "/move"), "{\"status\":\"nope\"}"), member))
                .andExpect(status().isBadRequest());
        mvc.perform(as(json(post(base + "/" + a + "/move"), "{}"), member)).andExpect(status().isBadRequest());
    }

    @Test
    void keepsTheOrderAfterManyDropsIntoTheSameGap() throws Exception {
        String top = createIssue(member, "top", "todo");
        createIssue(member, "bottom", "todo");
        List<String> expected = new ArrayList<>(List.of("top"));

        for (int i = 0; i < 45; i++) {
            String id = createIssue(member, "d" + i, "backlog");
            // Always drop directly under "top"; the neighbour below is whatever sits there at the moment.
            String neighbour = firstAfter(top);
            mvc.perform(as(json(post(base + "/" + id + "/move"),
                            "{\"status\":\"todo\",\"aboveId\":\"" + top + "\",\"belowId\":\"" + neighbour + "\"}"), member))
                    .andExpect(status().isOk());
            expected.add(1, "d" + i);
        }
        expected.add("bottom");

        assertThat(column("todo")).containsExactlyElementsOf(expected);
    }

    /** The ID of the issue directly below {@code above} in the todo column. */
    private String firstAfter(String above) throws Exception {
        String body = listBody("?status=todo");
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        List<Double> positions = JsonPath.read(body, "$.items[*].position");
        double abovePosition = positions.get(ids.indexOf(above));
        String best = null;
        double bestPosition = Double.MAX_VALUE;
        for (int i = 0; i < ids.size(); i++) {
            if (positions.get(i) > abovePosition && positions.get(i) < bestPosition) {
                best = ids.get(i);
                bestPosition = positions.get(i);
            }
        }
        return best;
    }

    // ---- deleting ----------------------------------------------------------------------------------------

    @Test
    void theCreatorAdminsAndOwnersCanDelete() throws Exception {
        String mine = createIssue(member, "mine", "todo");
        String second = createIssue(member, "second", "todo");
        String third = createIssue(member, "third", "todo");

        mvc.perform(as(delete(base + "/" + mine), member)).andExpect(status().isNoContent());
        mvc.perform(as(delete(base + "/" + second), admin)).andExpect(status().isNoContent());
        mvc.perform(as(delete(base + "/" + third), owner)).andExpect(status().isNoContent());

        assertThat(JsonPath.<Integer>read(listBody(""), "$.total")).isZero();
    }

    @Test
    void anotherPlainMemberCannotDelete() throws Exception {
        String id = createIssue(member, "keep", "todo");

        mvc.perform(as(delete(base + "/" + id), colleague)).andExpect(status().isForbidden());

        mvc.perform(get(base + "/" + id).cookie(member)).andExpect(status().isOk());
    }

    @Test
    void deletingAnIssueDeletesItsComments() throws Exception {
        String id = createIssue(member, "gone soon", "todo");
        mvc.perform(as(json(post(base + "/" + id + "/comments"), "{\"body\":\"hi\"}"), member))
                .andExpect(status().isCreated());
        assertThat(jdbc.sql("select count(*) from issue.comments where issue_id = :id")
                        .param("id", UUID.fromString(id)).query(Long.class).single()).isEqualTo(1);

        mvc.perform(as(delete(base + "/" + id), member)).andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from issue.comments where issue_id = :id")
                        .param("id", UUID.fromString(id)).query(Long.class).single()).isZero();
        mvc.perform(get(base + "/" + id).cookie(member)).andExpect(status().isNotFound());
    }

    // ---- comments ----------------------------------------------------------------------------------------

    @Test
    void addsAndListsComments() throws Exception {
        String id = createIssue(member, "Talk", "todo");

        mvc.perform(as(json(post(base + "/" + id + "/comments"), "{\"body\":\"  first\\nline two  \"}"), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("first\nline two"))
                .andExpect(jsonPath("$.author.type").value("member"))
                .andExpect(jsonPath("$.author.id").value(memberId))
                .andExpect(jsonPath("$.author.name").isNotEmpty());
        mvc.perform(as(json(post(base + "/" + id + "/comments"), "{\"body\":\"second\"}"), colleague))
                .andExpect(status().isCreated());

        mvc.perform(get(base + "/" + id + "/comments").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("first\nline two"))
                .andExpect(jsonPath("$[1].body").value("second"))
                .andExpect(jsonPath("$[1].author.id").value(colleagueId));
        assertThat(applicationEvents.stream(CommentAdded.class)).anySatisfy(
                event -> assertThat(event.issueId()).hasToString(id));
    }

    @Test
    void rejectsBlankAndOversizedComments() throws Exception {
        String id = createIssue(member, "Talk", "todo");

        for (String body : List.of("{}", "{\"body\":\"  \"}", "{\"body\":\"" + "x".repeat(10_001) + "\"}")) {
            mvc.perform(as(json(post(base + "/" + id + "/comments"), body), member)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(base + "/" + id + "/comments").cookie(member)).andExpect(jsonPath("$.length()").value(0));
    }

    // ---- who may do what ---------------------------------------------------------------------------------

    @Test
    void requiresALogin() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(issueJson("{\"title\":\"x\"}").with(xsrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(issueJson("{\"title\":\"x\"}").cookie(member)).andExpect(status().isForbidden());
        assertThat(JsonPath.<Integer>read(listBody(""), "$.total")).isZero();
    }

    @Test
    void anOutsiderGetsNotFoundOnEveryEndpointAndChangesNothing() throws Exception {
        String id = createIssue(member, "Private", "todo");
        String commentId = JsonPath.read(
                mvc.perform(as(json(post(base + "/" + id + "/comments"), "{\"body\":\"secret\"}"), member))
                        .andReturn().getResponse().getContentAsString(), "$.id");
        String myId = userId(outsider);

        mvc.perform(get(base).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(issueJson("{\"title\":\"x\"}"), outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/key/WEB-1").cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(patch(base + "/" + id), "{\"title\":\"hijacked\"}"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(put(base + "/" + id + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + myId + "\"}}"), outsider))
                .andExpect(status().isNotFound());
        mvc.perform(as(json(post(base + "/" + id + "/move"), "{\"status\":\"done\"}"), outsider))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete(base + "/" + id), outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id + "/comments").cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(post(base + "/" + id + "/comments"), "{\"body\":\"hi\"}"), outsider))
                .andExpect(status().isNotFound());

        mvc.perform(get(base + "/" + id).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.assignee").doesNotExist());
        mvc.perform(get(base + "/" + id + "/comments").cookie(member))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(commentId));
    }

    @Test
    void anIssueIsNotReachableThroughAnotherWorkspaceOfTheSameUser() throws Exception {
        String id = createIssue(member, "Only here", "todo");
        String otherWorkspace = createWorkspace(member, "Elsewhere " + UUID.randomUUID().toString().substring(0, 8));
        String otherBase = "/api/workspaces/" + otherWorkspace + "/issues";

        mvc.perform(get(otherBase + "/" + id).cookie(member)).andExpect(status().isNotFound());
        mvc.perform(as(json(patch(otherBase + "/" + id), "{\"title\":\"moved\"}"), member)).andExpect(status().isNotFound());
        mvc.perform(as(delete(otherBase + "/" + id), member)).andExpect(status().isNotFound());
        mvc.perform(get(otherBase + "/" + id + "/comments").cookie(member)).andExpect(status().isNotFound());
        mvc.perform(get(otherBase).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(base + "/" + id).cookie(member)).andExpect(jsonPath("$.title").value("Only here"));
    }
}
