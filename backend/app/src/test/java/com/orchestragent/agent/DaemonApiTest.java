package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.agent.application.RuntimeService;
import com.orchestragent.agent.application.TaskMaintenance;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The daemon API over HTTP: who may call it, what a daemon can reach, how work is handed out (never twice, even
 * to two daemons asking at the same moment), and what happens when a daemon goes quiet.
 */
class DaemonApiTest extends TaskWorkflowTestBase {

    @Autowired
    private TaskMaintenance maintenance;

    @Autowired
    private RuntimeService runtimeService;

    @BeforeEach
    void setUp() throws Exception {
        setUpWorld();
    }

    private String queuedTaskFor(String title) throws Exception {
        String issue = createIssue(title);
        assign(issue, agentId);
        return awaitNewestTask(issue, "queued");
    }

    // ---- who may call the daemon API ---------------------------------------------------------------------

    @Test
    void requiresAnApiToken() throws Exception {
        mvc.perform(get("/api/daemon/workspaces")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/daemon/tasks/claim").contentType("application/json").content("{\"runtimeIds\":[],\"maxTasks\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(daemon(get("/api/daemon/workspaces"), "oat_not-a-real-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void aBrowserSessionIsNotAnApiToken() throws Exception {
        // The daemon chain accepts tokens only: a signed-in browser (cookie, even with CSRF) is refused.
        mvc.perform(get("/api/daemon/workspaces").cookie(mia)).andExpect(status().isUnauthorized());
        mvc.perform(as(json(post("/api/daemon/tasks/claim"), "{\"runtimeIds\":[\"" + runtimeId + "\"],\"maxTasks\":1}"), mia))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anApiTokenIsNotABrowserSession() throws Exception {
        // And the other way round: a daemon token must not open the browser API.
        mvc.perform(get("/api/workspaces").header("Authorization", "Bearer " + miaToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/workspaces/" + workspaceId + "/issues/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", "Bearer " + miaToken))
                .andExpect(status().isUnauthorized());
    }

    // ---- workspaces and registration ---------------------------------------------------------------------

    @Test
    void theDaemonSeesTheWorkspacesOfTheTokenOwnerOnly() throws Exception {
        mvc.perform(daemon(get("/api/daemon/workspaces"), miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(workspaceId))
                .andExpect(jsonPath("$[0].slug").isNotEmpty());
        String outsiderToken = createToken(outsider);
        mvc.perform(daemon(get("/api/daemon/workspaces"), outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registeringTwiceKeepsOneRuntimePerProvider() throws Exception {
        String body = "{\"daemonId\":\"same-daemon\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId
                + "\",\"providers\":[{\"provider\":\"mock\",\"version\":\"1\"},{\"provider\":\"claude\",\"version\":\"2\"}]}";

        String first = mvc.perform(daemon(json(post("/api/daemon/register"), body), miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimes.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String again = mvc.perform(daemon(json(post("/api/daemon/register"), body), miaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(again, "$.runtimes[*].id"))
                .containsExactlyInAnyOrderElementsOf(JsonPath.<List<String>>read(first, "$.runtimes[*].id"));
        assertThat(JsonPath.<List<String>>read(again, "$.runtimes[*].status")).containsOnly("online");
    }

    @Test
    void aWorkspaceTheOwnerDoesNotBelongToCannotBeRegisteredIn() throws Exception {
        String outsiderToken = createToken(outsider);

        mvc.perform(daemon(json(post("/api/daemon/register"),
                        "{\"daemonId\":\"d\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId
                                + "\",\"providers\":[{\"provider\":\"mock\",\"version\":\"1\"}]}"), outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void aComputerAnotherUserRegisteredCannotBeTakenOver() throws Exception {
        String body = "{\"daemonId\":\"shared-id\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId
                + "\",\"providers\":[{\"provider\":\"mock\",\"version\":\"1\"}]}";
        mvc.perform(daemon(json(post("/api/daemon/register"), body), miaToken)).andExpect(status().isOk());

        mvc.perform(daemon(json(post("/api/daemon/register"), body), maxToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("runtime.owned_by_other"));
    }

    @Test
    void invalidRegistrationsAreRefused() throws Exception {
        for (String body : List.of(
                "{\"daemonId\":\"d\",\"deviceName\":\"Laptop\",\"providers\":[{\"provider\":\"mock\",\"version\":\"1\"}]}",
                "{\"daemonId\":\"d\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId + "\",\"providers\":[]}",
                "{\"daemonId\":\"d\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId + "\"}",
                "{\"daemonId\":\" \",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId + "\",\"providers\":[{\"provider\":\"mock\"}]}",
                "{\"daemonId\":\"d\",\"deviceName\":\"Laptop\",\"workspaceId\":\"" + workspaceId + "\",\"providers\":[{\"provider\":\"NOT VALID\"}]}")) {
            mvc.perform(daemon(json(post("/api/daemon/register"), body), miaToken)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void heartbeatsOnlyTouchTheOwnRuntimes() throws Exception {
        String maxsRuntime = registerRuntime(maxToken, "max-pc");

        mvc.perform(daemon(json(post("/api/daemon/heartbeat"), "{\"runtimeIds\":[\"" + runtimeId + "\",\"" + maxsRuntime + "\",\"" + UUID.randomUUID() + "\"]}"), miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeIds.length()").value(1))
                .andExpect(jsonPath("$.runtimeIds[0]").value(runtimeId));
    }

    // ---- claiming ----------------------------------------------------------------------------------------

    @Test
    void aClaimBriefsTheDaemonWithTheAgentTheIssueAndItsComments() throws Exception {
        String issue = createIssue("Brief me");
        comment(max, issue, "Context from Max");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");

        String response = claim(miaToken, 5, runtimeId);

        assertThat(claimedTaskIds(response)).containsExactly(task);
        assertThat(JsonPath.<String>read(response, "$.tasks[0].provider")).isEqualTo("mock");
        assertThat(JsonPath.<String>read(response, "$.tasks[0].runtimeId")).isEqualTo(runtimeId);
        assertThat(JsonPath.<String>read(response, "$.tasks[0].trigger")).isEqualTo("assigned");
        assertThat(JsonPath.<Integer>read(response, "$.tasks[0].attempt")).isEqualTo(1);
        assertThat(JsonPath.<String>read(response, "$.tasks[0].agent.id")).isEqualTo(agentId);
        assertThat(JsonPath.<String>read(response, "$.tasks[0].agent.name")).startsWith("Builder");
        assertThat(JsonPath.<String>read(response, "$.tasks[0].issue.title")).isEqualTo("Brief me");
        assertThat(JsonPath.<String>read(response, "$.tasks[0].issue.description")).isEqualTo("Details of Brief me");
        assertThat(JsonPath.<Integer>read(response, "$.tasks[0].issue.number")).isPositive();
        assertThat(JsonPath.<String>read(response, "$.tasks[0].issue.key")).matches("WEB-[0-9]+");
        assertThat(JsonPath.<List<String>>read(response, "$.tasks[0].comments[*].body")).containsExactly("Context from Max");
        assertThat(JsonPath.<List<String>>read(response, "$.tasks[0].comments[*].authorType")).containsExactly("member");
        assertThat(JsonPath.<Object>read(response, "$.tasks[0].priorSessionId")).isNull();
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("dispatched");
    }

    @Test
    void aTaskIsHandedOutOnlyOnce() throws Exception {
        queuedTaskFor("Once only");

        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).hasSize(1);
        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).isEmpty();
    }

    @Test
    void aDaemonOnlyGetsTheWorkOfItsOwnRuntimes() throws Exception {
        queuedTaskFor("Mia's work");
        String maxsRuntime = registerRuntime(maxToken, "max-pc");

        assertThat(claimedTaskIds(claim(maxToken, 5, runtimeId))).as("Mia's runtime, Max's token").isEmpty();
        assertThat(claimedTaskIds(claim(maxToken, 5, maxsRuntime))).as("Max's own runtime has no work").isEmpty();
        assertThat(claimedTaskIds(claim(miaToken, 5, maxsRuntime))).as("Max's runtime, Mia's token").isEmpty();
        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).hasSize(1);
    }

    @Test
    void aClaimNeverReturnsMoreThanAskedForAndTheOldestComeFirst() throws Exception {
        List<String> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(queuedTaskFor("Ordered " + i));
        }

        List<String> first = claimedTaskIds(claim(miaToken, 2, runtimeId));
        List<String> rest = claimedTaskIds(claim(miaToken, 10, runtimeId));

        assertThat(first).containsExactly(tasks.get(0), tasks.get(1));
        assertThat(rest).containsExactly(tasks.get(2), tasks.get(3));
    }

    @Test
    void twoDaemonsAskingAtTheSameMomentNeverGetTheSameTask() throws Exception {
        String secondRuntime = registerRuntime(miaToken, "mia-laptop");
        // Two agents, one per runtime, plus a shared pool: 10 tasks on the first runtime, claimed by two threads.
        int total = 10;
        for (int i = 0; i < total; i++) {
            queuedTaskFor("Race " + i);
        }
        assertThat(jdbc.sql("select count(*) from agent.agent_tasks where workspace_id = :ws and status = 'queued'")
                        .param("ws", UUID.fromString(workspaceId)).query(Long.class).single())
                .isEqualTo(total);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<List<String>>> jobs = new ArrayList<>();
            for (int thread = 0; thread < 2; thread++) {
                jobs.add(() -> {
                    go.await();
                    List<String> mine = new ArrayList<>();
                    for (int round = 0; round < 6; round++) {
                        mine.addAll(claimedTaskIds(claim(miaToken, 3, runtimeId, secondRuntime)));
                    }
                    return mine;
                });
            }
            List<Future<List<String>>> futures = new ArrayList<>();
            for (Callable<List<String>> job : jobs) {
                futures.add(pool.submit(job));
            }
            go.countDown();
            List<String> all = new ArrayList<>();
            for (Future<List<String>> future : futures) {
                all.addAll(future.get());
            }

            Set<String> distinct = new HashSet<>(all);
            assertThat(all).as("no task was handed out twice").hasSize(distinct.size());
            assertThat(distinct).as("every waiting task was handed out").hasSize(total);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.sql("select count(*) from agent.agent_tasks where workspace_id = :ws and status = 'queued'")
                        .param("ws", UUID.fromString(workspaceId)).query(Long.class).single())
                .isZero();
    }

    // ---- the life of a task ------------------------------------------------------------------------------

    @Test
    void theLogIsStoredInOrderAndResendingABatchStoresNothingTwice() throws Exception {
        String task = queuedTaskFor("Logged");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);
        String batch = "[{\"seq\":0,\"type\":\"text\",\"content\":\"Reading the code\"},{\"seq\":1,\"type\":\"tool_use\",\"content\":\"grep login\"}]";

        sendMessages(miaToken, task, batch);
        sendMessages(miaToken, task, batch);
        sendMessages(miaToken, task, "[{\"seq\":2,\"type\":\"tool_result\",\"content\":\"3 matches\"},{\"seq\":1,\"type\":\"text\",\"content\":\"changed my mind\"}]");

        String log = mvc.perform(get(tasksBase + "/" + task + "/messages").cookie(mia)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(log, "$[*].seq")).containsExactly(0, 1, 2);
        assertThat(JsonPath.<List<String>>read(log, "$[*].type")).containsExactly("text", "tool_use", "tool_result");
        assertThat(JsonPath.<List<String>>read(log, "$[*].content")).containsExactly("Reading the code", "grep login", "3 matches");
        String later = mvc.perform(get(tasksBase + "/" + task + "/messages?after=0").cookie(mia)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(later, "$[*].seq")).containsExactly(1, 2);
    }

    @Test
    void badLogLinesAreRefusedAndNothingFromTheBatchIsStored() throws Exception {
        String task = queuedTaskFor("Strict log");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        for (String batch : List.of(
                "[{\"seq\":0,\"type\":\"shout\",\"content\":\"x\"}]",
                "[{\"seq\":-1,\"type\":\"text\",\"content\":\"x\"}]",
                "[{\"seq\":0,\"type\":\"text\",\"content\":\"fine\"},{\"seq\":1,\"type\":\"nope\",\"content\":\"x\"}]")) {
            mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/messages"), "{\"messages\":" + batch + "}"), miaToken))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(get(tasksBase + "/" + task + "/messages").cookie(mia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void completingTwiceIsHarmlessAndTheResultIsCommentedOnce() throws Exception {
        String issue = createIssue("Repeated");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        assertThat(complete(miaToken, task, "The result", "session-1")).isEqualTo("completed");
        assertThat(complete(miaToken, task, "The result", "session-1")).isEqualTo("completed");

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentBodies(issue)).contains("The result"));
        assertStaysTrue(() -> {
            try {
                assertThat(commentBodies(issue).stream().filter("The result"::equals)).hasSize(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        String body = tasksBody(issue);
        assertThat(JsonPath.<Integer>read(body, "$[0].inputTokens")).isEqualTo(100);
        assertThat(JsonPath.<Integer>read(body, "$[0].outputTokens")).isEqualTo(40);
        assertThat(JsonPath.<Double>read(body, "$[0].costUsd")).isEqualTo(0.0123);
    }

    @Test
    void failingTwiceIsHarmlessAndTheFailureIsCommentedOnce() throws Exception {
        String issue = createIssue("Fails twice");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        assertThat(fail(miaToken, task, "It crashed", "agent_error")).isEqualTo("failed");
        assertThat(fail(miaToken, task, "It crashed", "agent_error")).isEqualTo("failed");

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentAuthorTypes(issue)).contains("system"));
        assertStaysTrue(() -> {
            try {
                assertThat(commentAuthorTypes(issue).stream().filter("system"::equals)).hasSize(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void aCancelledTaskIsNotStartedAndLateReportsAreIgnored() throws Exception {
        String issue = createIssue("Cancelled midway");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);
        mvc.perform(as(post(tasksBase + "/" + task + "/cancel"), mia)).andExpect(status().isOk());

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("cancelled");
        sendMessages(miaToken, task, "[{\"seq\":0,\"type\":\"text\",\"content\":\"late\"}]");
        assertThat(complete(miaToken, task, "Too late", null)).isEqualTo("cancelled");
        assertThat(fail(miaToken, task, "killed", "agent_error")).isEqualTo("cancelled");
        assertThat(start(miaToken, task)).isEqualTo("cancelled");

        mvc.perform(get(tasksBase + "/" + task + "/messages").cookie(mia)).andExpect(jsonPath("$.length()").value(0));
        assertStaysTrue(() -> {
            try {
                assertThat(commentBodies(issue)).doesNotContain("Too late");
                assertThat(issueStatus(issue)).isNotEqualTo("in_review");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void aTaskThatWasNeverStartedCannotBeCompleted() throws Exception {
        String task = queuedTaskFor("Skipped start");
        claim(miaToken, 1, runtimeId);

        mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/complete"), "{\"output\":\"x\"}"), miaToken))
                .andExpect(status().isConflict());
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("dispatched");
    }

    @Test
    void aTaskCanFailRightAfterTheClaim() throws Exception {
        String task = queuedTaskFor("Cannot prepare");
        claim(miaToken, 1, runtimeId);

        assertThat(fail(miaToken, task, "No space left for the work folder", "agent_error")).isEqualTo("failed");
    }

    @Test
    void aFailureWithoutAnExplanationStillFails() throws Exception {
        String task = queuedTaskFor("Silent failure");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/fail"), "{}"), miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    // ---- other people's tasks ----------------------------------------------------------------------------

    @Test
    void everyCallOnSomeoneElsesTaskIsNotFoundAndChangesNothing() throws Exception {
        String task = queuedTaskFor("Mia's task");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        mvc.perform(daemon(post("/api/daemon/tasks/" + task + "/start"), maxToken)).andExpect(status().isNotFound());
        mvc.perform(daemon(get("/api/daemon/tasks/" + task + "/status"), maxToken)).andExpect(status().isNotFound());
        mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/messages"), "{\"messages\":[]}"), maxToken))
                .andExpect(status().isNotFound());
        mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/complete"), "{\"output\":\"hijacked\"}"), maxToken))
                .andExpect(status().isNotFound());
        mvc.perform(daemon(json(post("/api/daemon/tasks/" + task + "/fail"), "{\"error\":\"sabotage\"}"), maxToken))
                .andExpect(status().isNotFound());
        mvc.perform(daemon(get("/api/daemon/tasks/" + UUID.randomUUID() + "/status"), miaToken)).andExpect(status().isNotFound());

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("running");
    }

    // ---- leases ------------------------------------------------------------------------------------------

    @Test
    void aTaskTheDaemonDidNotStartInTimeGoesBackToTheQueueAndCanBeClaimedAgain() throws Exception {
        String task = queuedTaskFor("Lost lease");
        claim(miaToken, 1, runtimeId);
        assertThat(maintenance.requeueExpiredLeases()).as("within the lease nothing happens").isZero();
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("dispatched");

        jdbc.sql("update agent.agent_tasks set lease_expires_at = :at where id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(5)))
                .param("id", UUID.fromString(task))
                .update();
        int count = maintenance.requeueExpiredLeases();

        assertThat(count).isEqualTo(1);
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("queued");
        mvc.perform(daemon(post("/api/daemon/tasks/" + task + "/start"), miaToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task.lease_lost"));
        assertThat(claimedTaskIds(claim(miaToken, 1, runtimeId))).containsExactly(task);
    }

    @Test
    void anExpiredTaskIsCancelledInsteadWhenANewerOneIsAlreadyWaiting() throws Exception {
        String issue = createIssue("Lease and comment");
        assign(issue, agentId);
        String expired = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        comment(max, issue, "Another request");
        awaitTaskCount(issue, 2);
        jdbc.sql("update agent.agent_tasks set lease_expires_at = :at where id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(5)))
                .param("id", UUID.fromString(expired))
                .update();

        maintenance.requeueExpiredLeases();

        assertThat(taskStatusFor(miaToken, expired)).isEqualTo("cancelled");
        assertThat(taskStatuses(issue)).containsExactly("queued", "cancelled");
    }

    // ---- computers that go away --------------------------------------------------------------------------

    @Test
    void aRunningTaskOfARuntimeThatWentOfflineFailsAndTheIssueIsBlockedWithAReason() throws Exception {
        String issue = createIssue("Computer vanishes");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);
        awaitIssueStatus(issue, "in_progress");
        jdbc.sql("update agent.runtimes set last_heartbeat_at = :at where id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(200)))
                .param("id", UUID.fromString(runtimeId))
                .update();

        runtimeService.markStaleOffline();
        int failed = maintenance.failTasksOfOfflineRuntimes();

        assertThat(failed).isEqualTo(1);
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("failed");
        String body = tasksBody(issue);
        assertThat(JsonPath.<String>read(body, "$[0].failureReason")).isEqualTo("runtime_offline");
        awaitIssueStatus(issue, "blocked");
        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(commentBodies(issue)).anyMatch(text -> text.contains("its computer went offline")));
    }

    @Test
    void aHeartbeatKeepsARuntimeAndItsRunningTaskAlive() throws Exception {
        String task = queuedTaskFor("Stays alive");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);
        jdbc.sql("update agent.runtimes set last_heartbeat_at = :at where id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(80)))
                .param("id", UUID.fromString(runtimeId))
                .update();
        mvc.perform(daemon(json(post("/api/daemon/heartbeat"), "{\"runtimeIds\":[\"" + runtimeId + "\"]}"), miaToken))
                .andExpect(status().isOk());

        runtimeService.markStaleOffline();

        assertThat(maintenance.failTasksOfOfflineRuntimes()).isZero();
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("running");
    }

    @Test
    void whenTheDaemonShutsDownItsRunningTasksFailAndWaitingOnesStay() throws Exception {
        String running = queuedTaskFor("Interrupted");
        claim(miaToken, 1, runtimeId);
        start(miaToken, running);
        String waiting = queuedTaskFor("Still waiting");

        mvc.perform(daemon(json(post("/api/daemon/deregister"), "{\"runtimeIds\":[\"" + runtimeId + "\"]}"), miaToken))
                .andExpect(status().isNoContent());

        assertThat(taskStatusFor(miaToken, running)).isEqualTo("failed");
        assertThat(taskStatusFor(miaToken, waiting)).isEqualTo("queued");
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(mia))
                .andExpect(jsonPath("$[?(@.id=='" + runtimeId + "')].status").value("offline"));
    }

    @Test
    void aDaemonCannotShutDownSomeoneElsesRuntime() throws Exception {
        String task = queuedTaskFor("Protected");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        mvc.perform(daemon(json(post("/api/daemon/deregister"), "{\"runtimeIds\":[\"" + runtimeId + "\"]}"), maxToken))
                .andExpect(status().isNoContent());

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("running");
        mvc.perform(get("/api/workspaces/" + workspaceId + "/runtimes").cookie(mia))
                .andExpect(jsonPath("$[?(@.id=='" + runtimeId + "')].status").value("online"));
    }
}
