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
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The agent and runtime REST API end to end: behaviour, workspace isolation and who may do what. */
class AgentApiTest extends IntegrationTest {

    @Autowired
    private RuntimeService runtimeService;

    private Cookie owner;
    private Cookie admin;
    private Cookie mia;
    private Cookie max;
    private Cookie outsider;
    private String miaId;
    private String maxId;
    private String workspaceId;
    private String miasRuntime;
    private String maxsRuntime;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = uniqueEmail();
        String miaEmail = uniqueEmail();
        String maxEmail = uniqueEmail();
        owner = login(uniqueEmail());
        admin = login(adminEmail);
        mia = login(miaEmail);
        max = login(maxEmail);
        outsider = login(uniqueEmail());
        miaId = userId(mia);
        maxId = userId(max);

        workspaceId = JsonPath.read(
                mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Agents " + UUID.randomUUID().toString().substring(0, 8) + "\"}")
                                .cookie(owner).with(xsrf()))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
        join(admin, adminEmail, "admin");
        join(mia, miaEmail, "member");
        join(max, maxEmail, "member");
        base = "/api/workspaces/" + workspaceId + "/agents";

        miasRuntime = register(miaId, "mia-pc", "Mia's PC");
        maxsRuntime = register(maxId, "max-pc", "Max's PC");
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private String userId(Cookie cookie) throws Exception {
        String body = mvc.perform(get("/api/me").cookie(cookie)).andReturn().getResponse().getContentAsString();
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

    /** What the daemon API will do in step 8: a daemon of this user registers a tool. */
    private String register(String userId, String daemonId, String device) {
        return runtimeService
                .register(UUID.fromString(userId), UUID.fromString(workspaceId), daemonId, device, List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id().toString();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    private static String body(String name, String runtimeId) {
        return "{\"name\":\"" + name + "\",\"instructions\":\"Do the work.\",\"runtimeId\":\"" + runtimeId + "\"}";
    }

    private String createAgent(Cookie by, String name, String runtimeId) throws Exception {
        String response = mvc.perform(as(json(post(base), body(name, runtimeId)), by))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private List<String> names(String query) throws Exception {
        return JsonPath.read(
                mvc.perform(get(base + query).cookie(mia)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$[*].name");
    }

    private String uniqueName() {
        return "Agent " + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- creating and reading ----------------------------------------------------------------------------

    @Test
    void createsAnAgentOnTheCreatorsRuntime() throws Exception {
        mvc.perform(as(json(post(base),
                        "{\"name\":\"  Builder  \",\"instructions\":\"# Rules\\n- be kind\",\"runtimeId\":\"" + miasRuntime
                                + "\",\"model\":\" sonnet \"}"), mia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Builder"))
                .andExpect(jsonPath("$.instructions").value("# Rules\n- be kind"))
                .andExpect(jsonPath("$.model").value("sonnet"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.createdById").value(miaId))
                .andExpect(jsonPath("$.createdByName").isNotEmpty())
                .andExpect(jsonPath("$.runtime.id").value(miasRuntime))
                .andExpect(jsonPath("$.runtime.deviceName").value("Mia's PC"))
                .andExpect(jsonPath("$.runtime.provider").value("claude"))
                .andExpect(jsonPath("$.runtime.status").value("online"));
    }

    @Test
    void theModelAndInstructionsAreOptional() throws Exception {
        mvc.perform(as(json(post(base), "{\"name\":\"" + uniqueName() + "\",\"runtimeId\":\"" + miasRuntime + "\"}"), mia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructions").value(""))
                .andExpect(jsonPath("$.model").doesNotExist());
    }

    @Test
    void validatesTheInput() throws Exception {
        for (String json : List.of(
                "{}",
                "{\"name\":\"   \",\"runtimeId\":\"" + miasRuntime + "\"}",
                "{\"name\":\"" + "x".repeat(61) + "\",\"runtimeId\":\"" + miasRuntime + "\"}",
                "{\"name\":\"two\\nlines\",\"runtimeId\":\"" + miasRuntime + "\"}",
                "{\"name\":\"ok\"}",
                "{\"name\":\"ok\",\"runtimeId\":\"not-a-uuid\"}",
                "{\"name\":\"ok\",\"runtimeId\":\"" + miasRuntime + "\",\"instructions\":\"" + "x".repeat(20_001) + "\"}",
                "{\"name\":\"ok\",\"runtimeId\":\"" + miasRuntime + "\",\"model\":\"" + "x".repeat(101) + "\"}")) {
            mvc.perform(as(json(post(base), json), mia)).andExpect(status().isBadRequest());
        }
        assertThat(names("?includeArchived=true")).isEmpty();
    }

    @Test
    void theRuntimeMustExistInTheWorkspaceAndBelongToTheCreator() throws Exception {
        mvc.perform(as(json(post(base), body(uniqueName(), UUID.randomUUID().toString())), mia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("agent.invalid_runtime"));
        mvc.perform(as(json(post(base), body(uniqueName(), maxsRuntime)), mia))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent.runtime_not_yours"));
        assertThat(names("?includeArchived=true")).isEmpty();
    }

    @Test
    void aRuntimeOfAnotherWorkspaceIsNotUsable() throws Exception {
        String other = JsonPath.read(
                mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Other " + UUID.randomUUID().toString().substring(0, 8) + "\"}")
                                .cookie(mia).with(xsrf()))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
        String foreign = runtimeService
                .register(UUID.fromString(miaId), UUID.fromString(other), "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id().toString();

        mvc.perform(as(json(post(base), body(uniqueName(), foreign)), mia)).andExpect(status().isBadRequest());
    }

    @Test
    void activeNamesAreUniqueIgnoringCase() throws Exception {
        String name = uniqueName();
        createAgent(mia, name, miasRuntime);

        mvc.perform(as(json(post(base), body(name.toUpperCase(), miasRuntime)), mia))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.name_taken"));
    }

    @Test
    void twoCreatesWithTheSameNameAtOnceNeverBothSucceed() throws Exception {
        String name = uniqueName();
        var postProcessor = xsrf();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                jobs.add(() -> mvc.perform(json(post(base), body(name, miasRuntime)).cookie(mia).with(postProcessor))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(jobs)) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(names("")).containsOnlyOnce(name);
    }

    @Test
    void anyMemberListsAndReadsAgentsArchivedOnesOnlyOnRequest() throws Exception {
        String active = uniqueName();
        String gone = uniqueName();
        createAgent(mia, active, miasRuntime);
        String goneId = createAgent(mia, gone, miasRuntime);
        mvc.perform(as(post(base + "/" + goneId + "/archive"), mia)).andExpect(status().isOk());

        assertThat(names("")).containsExactly(active);
        assertThat(names("?includeArchived=true")).containsExactlyInAnyOrder(active, gone);
        mvc.perform(get(base + "/" + goneId).cookie(max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }

    @Test
    void agentsOfOtherWorkspacesNeverShowUp() throws Exception {
        String other = JsonPath.read(
                mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Elsewhere " + UUID.randomUUID().toString().substring(0, 8) + "\"}")
                                .cookie(mia).with(xsrf()))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
        String otherRuntime = runtimeService
                .register(UUID.fromString(miaId), UUID.fromString(other), "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id().toString();
        String otherBase = "/api/workspaces/" + other + "/agents";
        String foreign = JsonPath.read(
                mvc.perform(as(json(post(otherBase), body("Foreign", otherRuntime)), mia)).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        assertThat(names("?includeArchived=true")).doesNotContain("Foreign");
        mvc.perform(get(base + "/" + foreign).cookie(mia)).andExpect(status().isNotFound());
        mvc.perform(as(json(put(base + "/" + foreign), body("Hijack", miasRuntime)), mia)).andExpect(status().isNotFound());
        mvc.perform(as(post(base + "/" + foreign + "/archive"), mia)).andExpect(status().isNotFound());
        mvc.perform(get(otherBase + "/" + foreign).cookie(mia)).andExpect(status().isOk());
    }

    // ---- editing, archiving, restoring -------------------------------------------------------------------

    @Test
    void theCreatorEditsTheAgentAndOthersSeeTheChange() throws Exception {
        String id = createAgent(mia, uniqueName(), miasRuntime);
        String newName = uniqueName();

        mvc.perform(as(json(put(base + "/" + id),
                        "{\"name\":\"" + newName + "\",\"instructions\":\"Review.\",\"runtimeId\":\"" + miasRuntime + "\",\"model\":\"opus\"}"), mia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.model").value("opus"));
        mvc.perform(get(base + "/" + id).cookie(max))
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.instructions").value("Review."));
    }

    @Test
    void anAgentCanBeSavedWithTheNameItAlreadyHas() throws Exception {
        String name = uniqueName();
        String id = createAgent(mia, name, miasRuntime);

        mvc.perform(as(json(put(base + "/" + id), body(name, miasRuntime)), mia)).andExpect(status().isOk());
    }

    @Test
    void renamingToTheNameOfAnotherActiveAgentIsAConflict() throws Exception {
        String taken = uniqueName();
        createAgent(mia, taken, miasRuntime);
        String id = createAgent(mia, uniqueName(), miasRuntime);

        mvc.perform(as(json(put(base + "/" + id), body(taken.toLowerCase(), miasRuntime)), mia))
                .andExpect(status().isConflict());
    }

    @Test
    void adminsAndOwnersManageAnyAgentButAPlainMemberDoesNot() throws Exception {
        String id = createAgent(mia, uniqueName(), miasRuntime);

        mvc.perform(as(json(put(base + "/" + id), body(uniqueName(), miasRuntime)), admin)).andExpect(status().isOk());
        mvc.perform(as(json(put(base + "/" + id), body(uniqueName(), miasRuntime)), owner)).andExpect(status().isOk());
        mvc.perform(as(json(put(base + "/" + id), body("Mine now", miasRuntime)), max))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent.manage_forbidden"));
        mvc.perform(as(post(base + "/" + id + "/archive"), max)).andExpect(status().isForbidden());
        mvc.perform(as(post(base + "/" + id + "/archive"), admin)).andExpect(status().isOk());
        mvc.perform(as(post(base + "/" + id + "/restore"), max)).andExpect(status().isForbidden());
        mvc.perform(as(post(base + "/" + id + "/restore"), owner)).andExpect(status().isOk());
    }

    @Test
    void movingAnAgentToSomeoneElsesRuntimeIsForbiddenEvenForAnAdmin() throws Exception {
        String id = createAgent(mia, uniqueName(), miasRuntime);

        mvc.perform(as(json(put(base + "/" + id), body("Builder", maxsRuntime)), mia)).andExpect(status().isForbidden());
        mvc.perform(as(json(put(base + "/" + id), body("Builder", maxsRuntime)), admin)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/" + id).cookie(mia)).andExpect(jsonPath("$.runtime.id").value(miasRuntime));
    }

    @Test
    void anArchivedAgentCannotBeEditedUntilRestored() throws Exception {
        String id = createAgent(mia, uniqueName(), miasRuntime);
        mvc.perform(as(post(base + "/" + id + "/archive"), mia)).andExpect(status().isOk());

        mvc.perform(as(json(put(base + "/" + id), body(uniqueName(), miasRuntime)), mia))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.archived"));
        mvc.perform(as(post(base + "/" + id + "/restore"), mia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(as(json(put(base + "/" + id), body(uniqueName(), miasRuntime)), mia)).andExpect(status().isOk());
    }

    @Test
    void anArchivedNameCanBeReusedAndThenTheOldAgentCannotBeRestored() throws Exception {
        String name = uniqueName();
        String old = createAgent(mia, name, miasRuntime);
        mvc.perform(as(post(base + "/" + old + "/archive"), mia)).andExpect(status().isOk());
        createAgent(mia, name, miasRuntime);

        mvc.perform(as(post(base + "/" + old + "/restore"), mia))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.name_taken"));
    }

    // ---- runtimes ----------------------------------------------------------------------------------------

    @Test
    void membersSeeTheRuntimesOfTheWorkspace() throws Exception {
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(miasRuntime))
                .andExpect(jsonPath("$[0].ownerId").value(miaId))
                .andExpect(jsonPath("$[0].ownerName").isNotEmpty())
                .andExpect(jsonPath("$[0].deviceName").value("Mia's PC"))
                .andExpect(jsonPath("$[0].provider").value("claude"))
                .andExpect(jsonPath("$[0].providerVersion").value("1.0"))
                .andExpect(jsonPath("$[0].status").value("online"))
                .andExpect(jsonPath("$[0].lastHeartbeatAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(maxsRuntime));
    }

    @Test
    void theJobMarksSilentRuntimesOfflineInTheDatabaseAndTheApiShowsIt() throws Exception {
        // Mia's daemon went quiet two minutes ago; Max's just sent a heartbeat.
        jdbc.sql("update agent.runtimes set last_heartbeat_at = :at where id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(120)))
                .param("id", UUID.fromString(miasRuntime))
                .update();
        runtimeService.heartbeat(UUID.fromString(maxId), List.of(UUID.fromString(maxsRuntime)));

        int count = runtimeService.markStaleOffline();

        assertThat(count).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(max))
                .andExpect(jsonPath("$[0].status").value("offline"))
                .andExpect(jsonPath("$[1].status").value("online"));
        mvc.perform(get(base + "/" + createAgent(max, uniqueName(), maxsRuntime)).cookie(max))
                .andExpect(jsonPath("$.runtime.status").value("online"));
    }

    @Test
    void aHeartbeatBringsAnOfflineRuntimeBack() throws Exception {
        jdbc.sql("update agent.runtimes set status = 'offline' where id = :id")
                .param("id", UUID.fromString(miasRuntime))
                .update();

        runtimeService.heartbeat(UUID.fromString(miaId), List.of(UUID.fromString(miasRuntime)));

        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(max))
                .andExpect(jsonPath("$[0].status").value("online"));
    }

    @Test
    void registeringTheSameComputerAgainKeepsOneRuntime() throws Exception {
        String again = register(miaId, "mia-pc", "Mia's renamed PC");

        assertThat(again).isEqualTo(miasRuntime);
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(max))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].deviceName").value("Mia's renamed PC"));
    }

    // ---- who may do what ---------------------------------------------------------------------------------

    @Test
    void requiresALogin() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes")).andExpect(status().isUnauthorized());
        mvc.perform(json(post(base), body("x", miasRuntime)).with(xsrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(json(post(base), body(uniqueName(), miasRuntime)).cookie(mia)).andExpect(status().isForbidden());
        assertThat(names("?includeArchived=true")).isEmpty();
    }

    @Test
    void anOutsiderGetsNotFoundOnEveryEndpointAndChangesNothing() throws Exception {
        String name = uniqueName();
        String id = createAgent(mia, name, miasRuntime);

        mvc.perform(get(base).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(post(base), body("Sneaky", miasRuntime)), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(put(base + "/" + id), body("Hijack", miasRuntime)), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(base + "/" + id + "/archive"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(base + "/" + id + "/restore"), outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(outsider)).andExpect(status().isNotFound());

        mvc.perform(get(base + "/" + id).cookie(mia))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.archived").value(false));
    }
}
