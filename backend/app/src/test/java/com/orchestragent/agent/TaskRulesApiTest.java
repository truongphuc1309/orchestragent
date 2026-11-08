package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * When an agent gets work, end to end: the rules of the table in PLAN.md, with real events between the issue and
 * agent contexts, a real database, and a daemon played with HTTP calls.
 */
class TaskRulesApiTest extends TaskWorkflowTestBase {

    @BeforeEach
    void setUp() throws Exception {
        setUpWorld();
    }

    // ---- assigning ---------------------------------------------------------------------------------------

    @Test
    void assigningAnIssueToAnAgentQueuesATaskOnItsRuntime() throws Exception {
        String issue = createIssue("Fix login");

        assign(issue, agentId);

        String task = awaitNewestTask(issue, "queued");
        String body = tasksBody(issue);
        assertThat(JsonPath.<String>read(body, "$[0].id")).isEqualTo(task);
        assertThat(JsonPath.<String>read(body, "$[0].trigger")).isEqualTo("assigned");
        assertThat(JsonPath.<Integer>read(body, "$[0].attempt")).isEqualTo(1);
        assertThat(JsonPath.<String>read(body, "$[0].agentId")).isEqualTo(agentId);
        assertThat(JsonPath.<String>read(body, "$[0].agentName")).startsWith("Builder");
        assertThat(JsonPath.<String>read(body, "$[0].runtime.id")).isEqualTo(runtimeId);
        assertThat(JsonPath.<String>read(body, "$[0].runtime.status")).isEqualTo("online");
    }

    @Test
    void creatingAnIssueAlreadyAssignedToAnAgentQueuesATask() throws Exception {
        String issue = JsonPath.read(
                mvc.perform(as(json(post(issuesBase),
                                "{\"projectId\":\"" + projectIn(workspaceId, mia) + "\",\"title\":\"Born assigned\",\"assignee\":{\"type\":\"agent\",\"id\":\"" + agentId + "\"}}"), mia))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        awaitNewestTask(issue, "queued");
    }

    @Test
    void noTaskForAnIssueThatIsDoneOrCancelled() throws Exception {
        String done = createIssue("Finished work", "done");
        String cancelled = createIssue("Dropped work", "cancelled");
        String open = createIssue("Open work");

        assign(done, agentId);
        assign(cancelled, agentId);
        assign(open, agentId);

        awaitTaskCount(open, 1);
        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(done)).isEmpty();
                assertThat(taskIds(cancelled)).isEmpty();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void noTaskWhenAssigningToAMember() throws Exception {
        String issue = createIssue("For a person");
        assign(issue, agentId);
        awaitTaskCount(issue, 1);

        mvc.perform(as(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(issuesBase + "/" + issue + "/assignee"),
                        "{\"assignee\":{\"type\":\"member\",\"id\":\"" + maxId + "\"}}"), mia))
                .andExpect(status().isOk());

        assertThat(awaitNewestTask(issue, "cancelled")).isNotBlank();
        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).hasSize(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    // ---- comments ----------------------------------------------------------------------------------------

    @Test
    void aPersonsCommentQueuesATaskAfterTheFirstOneIsDone() throws Exception {
        String issue = createIssue("Chatty");
        assign(issue, agentId);
        String first = awaitNewestTask(issue, "queued");
        claimedTaskIds(claim(miaToken, 5, runtimeId));
        start(miaToken, first);
        complete(miaToken, first, "First answer", "session-1");
        awaitNewestTask(issue, "completed");

        comment(max, issue, "One more thing: add a test.");

        awaitTaskCount(issue, 2);
        assertThat(JsonPath.<String>read(tasksBody(issue), "$[0].trigger")).isEqualTo("comment");
        assertThat(taskStatuses(issue)).containsExactly("queued", "completed");
    }

    @Test
    void severalCommentsWhileATaskWaitsMakeNoSecondTask() throws Exception {
        String issue = createIssue("Waiting");
        assign(issue, agentId);
        awaitNewestTask(issue, "queued");

        comment(max, issue, "Also this.");
        comment(max, issue, "And that.");
        comment(olivia, issue, "Please hurry.");

        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).hasSize(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void aCommentWhileATaskRunsQueuesTheNextOneAndItWaitsForTheFirst() throws Exception {
        String issue = createIssue("Busy");
        assign(issue, agentId);
        String first = awaitNewestTask(issue, "queued");
        claim(miaToken, 5, runtimeId);
        start(miaToken, first);

        comment(max, issue, "While you work: use tabs.");

        awaitTaskCount(issue, 2);
        assertThat(taskStatuses(issue)).containsExactly("queued", "running");
        // The queued task is not handed out while the first one runs.
        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).isEmpty();
        complete(miaToken, first, "Done", null);
        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).hasSize(1);
    }

    @Test
    void aCommentOfAnAgentNeverStartsMoreWork() throws Exception {
        String issue = createIssue("No loops");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 5, runtimeId);
        start(miaToken, task);
        complete(miaToken, task, "All done here.", "session-1");

        // The result is written as a comment of the agent, which must not wake the agent up again.
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentBodies(issue)).contains("All done here."));
        assertThat(commentAuthorTypes(issue)).contains("agent");
        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).hasSize(1);
                assertThat(taskStatuses(issue)).containsExactly("completed");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void aSystemCommentNeverStartsMoreWorkEither() throws Exception {
        String issue = createIssue("Failing quietly");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 5, runtimeId);
        start(miaToken, task);
        fail(miaToken, task, "The tool crashed", "agent_error");

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentAuthorTypes(issue)).contains("system"));
        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).hasSize(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void aCommentOnAnIssueOfAMemberQueuesNothing() throws Exception {
        String issue = createIssue("Human work");
        comment(max, issue, "Anyone there?");

        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).isEmpty();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    // ---- taking the issue away from the agent ------------------------------------------------------------

    @Test
    void unassigningCancelsAQueuedAndARunningTaskAndTheDaemonLearnsIt() throws Exception {
        String waiting = createIssue("Waiting issue");
        String running = createIssue("Running issue");
        assign(running, agentId);
        String runningTask = awaitNewestTask(running, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, runningTask);
        assign(waiting, agentId);
        String waitingTask = awaitNewestTask(waiting, "queued");

        unassign(running);
        unassign(waiting);

        awaitNewestTask(running, "cancelled");
        awaitNewestTask(waiting, "cancelled");
        assertThat(taskStatusFor(miaToken, runningTask)).isEqualTo("cancelled");
        assertThat(taskStatusFor(miaToken, waitingTask)).isEqualTo("cancelled");
    }

    @Test
    void reassigningToAnotherAgentCancelsTheOldWorkAndQueuesNewWork() throws Exception {
        String second = createAgent(mia, "Reviewer " + shortId(), runtimeId);
        String issue = createIssue("Passed on");
        assign(issue, agentId);
        String oldTask = awaitNewestTask(issue, "queued");

        assign(issue, second);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(taskStatuses(issue)).containsExactlyInAnyOrder("queued", "cancelled"));
        assertThat(taskStatusFor(miaToken, oldTask)).isEqualTo("cancelled");
        List<String> agentIdsOfTasks = JsonPath.read(tasksBody(issue), "$[?(@.status=='queued')].agentId");
        assertThat(agentIdsOfTasks).containsExactly(second);
    }

    @Test
    void deletingTheIssueCancelsItsTasks() throws Exception {
        String issue = createIssue("Doomed");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 5, runtimeId);
        start(miaToken, task);

        mvc.perform(as(delete(issuesBase + "/" + issue), mia)).andExpect(status().isNoContent());

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(taskStatusFor(miaToken, task)).isEqualTo("cancelled"));
    }

    @Test
    void archivingTheAgentCancelsItsWork() throws Exception {
        String issue = createIssue("Orphaned");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");

        mvc.perform(as(post(agentsBase + "/" + agentId + "/archive"), mia)).andExpect(status().isOk());

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("cancelled");
        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).isEmpty();
    }

    @Test
    void anAgentThatMovesToAnotherRuntimeTakesItsWaitingTaskAlong() throws Exception {
        String secondRuntime = registerRuntime(miaToken, "mia-laptop");
        String issue = createIssue("Follows the agent");
        assign(issue, agentId);
        awaitNewestTask(issue, "queued");

        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(agentsBase + "/" + agentId)
                        .contentType("application/json")
                        .content("{\"name\":\"Builder " + shortId() + "\",\"runtimeId\":\"" + secondRuntime + "\"}"), mia))
                .andExpect(status().isOk());

        assertThat(claimedTaskIds(claim(miaToken, 5, runtimeId))).as("the old runtime has nothing").isEmpty();
        assertThat(claimedTaskIds(claim(miaToken, 5, secondRuntime))).hasSize(1);
    }

    // ---- the web API around tasks ------------------------------------------------------------------------

    @Test
    void membersCancelAWaitingTaskAndCancellingAgainIsHarmless() throws Exception {
        String issue = createIssue("Cancel me");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");

        mvc.perform(as(post(tasksBase + "/" + task + "/cancel"), max))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("cancelled"));
        mvc.perform(as(post(tasksBase + "/" + task + "/cancel"), max))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("cancelled"));

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("cancelled");
    }

    @Test
    void outsidersGetNotFoundOnEveryTaskEndpoint() throws Exception {
        String issue = createIssue("Private");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");

        mvc.perform(get(tasksBase + "?issueId=" + issue).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(tasksBase + "/" + task + "/messages").cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(tasksBase + "/" + task + "/cancel"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(tasksBase + "/" + task + "/retry"), outsider)).andExpect(status().isNotFound());
        assertThat(taskStatusFor(miaToken, task)).isEqualTo("queued");
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        String issue = createIssue("Protected");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");

        mvc.perform(post(tasksBase + "/" + task + "/cancel").cookie(mia)).andExpect(status().isForbidden());

        assertThat(taskStatusFor(miaToken, task)).isEqualTo("queued");
    }
}
