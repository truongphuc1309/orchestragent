package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * From assigning an issue to an agent to a person accepting its work: the whole loop through the real API, with
 * the daemon played by HTTP calls. The review gate is the point: an agent finishes into "in review", and only a
 * person can move the issue on.
 */
class ReviewWorkflowApiTest extends TaskWorkflowTestBase {

    @BeforeEach
    void setUp() throws Exception {
        setUpWorld();
    }

    /** Assigns, claims, starts and completes one run; returns the task ID. The issue ends up in review. */
    private String runToReview(String issue, String output, String sessionId) throws Exception {
        String task = awaitNewestTask(issue, "queued");
        assertThat(claimedTaskIds(claim(miaToken, 1, runtimeId))).containsExactly(task);
        start(miaToken, task);
        complete(miaToken, task, output, sessionId);
        awaitIssueStatus(issue, "in_review");
        return task;
    }

    // ---- the whole loop ----------------------------------------------------------------------------------

    @Test
    void fromAssignmentToDoneThroughReview() throws Exception {
        String issue = createIssue("Fix the login");
        comment(max, issue, "Users cannot sign in on mobile.");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        assertThat(issueStatus(issue)).as("assigning alone does not move the issue").isEqualTo("todo");

        claim(miaToken, 1, runtimeId);
        start(miaToken, task);
        awaitIssueStatus(issue, "in_progress");
        sendMessages(miaToken, task, "[{\"seq\":0,\"type\":\"text\",\"content\":\"Looking into it\"}]");
        complete(miaToken, task, "I fixed the login on mobile.", "session-1");

        awaitIssueStatus(issue, "in_review");
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentBodies(issue)).contains("I fixed the login on mobile."));
        assertThat(commentAuthorTypes(issue)).containsExactly("member", "agent");
        assertThat(taskStatuses(issue)).containsExactly("completed");

        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));
        assertThat(issueStatus(issue)).isEqualTo("done");
    }

    @Test
    void anAgentFinishingNeverMarksTheIssueDoneItself() throws Exception {
        String issue = createIssue("Needs a human");
        assign(issue, agentId);

        runToReview(issue, "Finished", null);

        assertStaysTrue(() -> {
            try {
                assertThat(issueStatus(issue)).isEqualTo("in_review");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void sendingTheWorkBackReturnsTheIssueToTodoAndTheAgentReworksItInTheSameSession() throws Exception {
        String issue = createIssue("Rework me");
        assign(issue, agentId);
        String first = runToReview(issue, "First attempt", "session-1");

        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"Please add a test.\"}"), olivia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("todo"));

        awaitTaskCount(issue, 2);
        String body = tasksBody(issue);
        assertThat(JsonPath.<String>read(body, "$[0].trigger")).isEqualTo("changes_requested");
        assertThat(JsonPath.<String>read(body, "$[0].status")).isEqualTo("queued");
        String claimed = claim(miaToken, 1, runtimeId);
        assertThat(JsonPath.<String>read(claimed, "$.tasks[0].trigger")).isEqualTo("changes_requested");
        assertThat(JsonPath.<String>read(claimed, "$.tasks[0].priorSessionId")).isEqualTo("session-1");
        assertThat(JsonPath.<String>read(claimed, "$.tasks[0].priorWorkDir")).isEqualTo("/work/" + first);
        assertThat(JsonPath.<List<String>>read(claimed, "$.tasks[0].comments[*].body"))
                .containsExactly("First attempt", "Please add a test.");
        assertThat(JsonPath.<List<String>>read(claimed, "$.tasks[0].comments[*].authorType"))
                .containsExactly("agent", "member");
        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).as("one task for the comment, not two").hasSize(2);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void thePersonWhoSendsWorkBackSeesTheirCommentOnTheIssue() throws Exception {
        String issue = createIssue("Feedback");
        assign(issue, agentId);
        runToReview(issue, "Done", null);

        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"  Too slow.\\n\\nMake it faster.  \"}"), max))
                .andExpect(status().isOk());

        assertThat(commentBodies(issue)).containsExactly("Done", "Too slow.\n\nMake it faster.");
        assertThat(commentAuthorTypes(issue)).containsExactly("agent", "member");
    }

    @Test
    void theAgentCanBeSentBackMoreThanOnce() throws Exception {
        String issue = createIssue("Several rounds");
        assign(issue, agentId);
        runToReview(issue, "Round one", "s1");
        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"Again.\"}"), max))
                .andExpect(status().isOk());
        awaitTaskCount(issue, 2);
        String second = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, second);
        complete(miaToken, second, "Round two", "s2");
        awaitIssueStatus(issue, "in_review");

        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), max)).andExpect(status().isOk());

        assertThat(issueStatus(issue)).isEqualTo("done");
        assertThat(taskStatuses(issue)).containsExactly("completed", "completed");
    }

    // ---- failure and retry -------------------------------------------------------------------------------

    @Test
    void aFailedRunBlocksTheIssueWithASystemCommentAndCanBeRetried() throws Exception {
        String issue = createIssue("Will fail");
        assign(issue, agentId);
        String task = awaitNewestTask(issue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, task);

        fail(miaToken, task, "The tool did not answer for 30 minutes.", "timeout");

        awaitIssueStatus(issue, "blocked");
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(commentBodies(issue)).hasSize(1));
        assertThat(commentAuthorTypes(issue)).containsExactly("system");
        assertThat(commentBodies(issue).getFirst())
                .startsWith("The agent could not finish this task: it ran out of time.")
                .contains("The tool did not answer for 30 minutes.");
        assertThat(JsonPath.<String>read(tasksBody(issue), "$[0].failureReason")).isEqualTo("timeout");

        mvc.perform(as(post(tasksBase + "/" + task + "/retry"), max))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.attempt").value(2))
                .andExpect(jsonPath("$.trigger").value("retry"));
        String claimed = claim(miaToken, 1, runtimeId);
        assertThat(JsonPath.<Integer>read(claimed, "$.tasks[0].attempt")).isEqualTo(2);
        assertThat(JsonPath.<String>read(claimed, "$.tasks[0].trigger")).isEqualTo("retry");
        assertThat(JsonPath.<String>read(claimed, "$.tasks[0].priorSessionId")).as("a failed run leaves nothing to resume").isNull();
    }

    @Test
    void onlyAFailedTaskCanBeRetriedAndOnlyOnceWhileOneWaits() throws Exception {
        String issue = createIssue("Retry rules");
        assign(issue, agentId);
        String waiting = awaitNewestTask(issue, "queued");

        mvc.perform(as(post(tasksBase + "/" + waiting + "/retry"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task.not_failed"));

        claim(miaToken, 1, runtimeId);
        start(miaToken, waiting);
        fail(miaToken, waiting, "boom", "agent_error");
        mvc.perform(as(post(tasksBase + "/" + waiting + "/retry"), max)).andExpect(status().isCreated());
        mvc.perform(as(post(tasksBase + "/" + waiting + "/retry"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task.already_queued"));
        assertThat(taskIds(issue)).hasSize(2);
    }

    @Test
    void noRetryOnceTheAgentIsArchivedOrTheIssueMovedToSomeoneElse() throws Exception {
        String archivedIssue = createIssue("Archived agent");
        assign(archivedIssue, agentId);
        String first = awaitNewestTask(archivedIssue, "queued");
        claim(miaToken, 1, runtimeId);
        start(miaToken, first);
        fail(miaToken, first, "boom", "agent_error");
        mvc.perform(as(post(agentsBase + "/" + agentId + "/archive"), mia)).andExpect(status().isOk());

        mvc.perform(as(post(tasksBase + "/" + first + "/retry"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.archived"));

        mvc.perform(as(post(agentsBase + "/" + agentId + "/restore"), mia)).andExpect(status().isOk());
        unassign(archivedIssue);
        mvc.perform(as(post(tasksBase + "/" + first + "/retry"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task.agent_not_assigned"));
    }

    // ---- the review gate itself --------------------------------------------------------------------------

    @Test
    void onlyAnIssueInReviewCanBeApprovedOrSentBack() throws Exception {
        String issue = createIssue("Not ready");

        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("issue.not_in_review"));
        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"Redo\"}"), max))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("issue.not_in_review"));

        assertThat(issueStatus(issue)).isEqualTo("todo");
        assertThat(commentBodies(issue)).isEmpty();
    }

    @Test
    void theCommentIsRequiredWhenSendingWorkBack() throws Exception {
        String issue = createIssue("Needs words");
        assign(issue, agentId);
        runToReview(issue, "Done", null);

        for (String body : List.of("{}", "{\"comment\":\"\"}", "{\"comment\":\"   \"}", "{\"comment\":\"" + "x".repeat(10_001) + "\"}")) {
            mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), body), max))
                    .andExpect(status().isBadRequest());
        }

        assertThat(issueStatus(issue)).isEqualTo("in_review");
        assertThat(commentBodies(issue)).containsExactly("Done");
    }

    @Test
    void twoReviewersCannotBothDecide() throws Exception {
        String issue = createIssue("Two reviewers");
        assign(issue, agentId);
        runToReview(issue, "Done", null);
        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), max)).andExpect(status().isOk());

        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"Actually no.\"}"), olivia))
                .andExpect(status().isConflict());
        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), olivia)).andExpect(status().isConflict());

        assertThat(issueStatus(issue)).isEqualTo("done");
    }

    @Test
    void outsidersCannotReviewAndAnIssueOfAnotherWorkspaceCannotBeReachedThroughThisOne() throws Exception {
        String issue = createIssue("Private review");
        assign(issue, agentId);
        runToReview(issue, "Done", null);

        mvc.perform(as(post(issuesBase + "/" + issue + "/approve"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"No\"}"), outsider))
                .andExpect(status().isNotFound());
        mvc.perform(as(post("/api/workspaces/" + java.util.UUID.randomUUID() + "/issues/" + issue + "/approve"), mia))
                .andExpect(status().isNotFound());

        assertThat(issueStatus(issue)).isEqualTo("in_review");
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        String issue = createIssue("Protected review");
        assign(issue, agentId);
        runToReview(issue, "Done", null);

        mvc.perform(post(issuesBase + "/" + issue + "/approve").cookie(mia)).andExpect(status().isForbidden());

        assertThat(issueStatus(issue)).isEqualTo("in_review");
    }

    @Test
    void sendingWorkBackOnAnIssueWithoutAnAgentJustMovesItBack() throws Exception {
        String issue = createIssue("Person's work", "in_review");

        mvc.perform(as(json(post(issuesBase + "/" + issue + "/request-changes"), "{\"comment\":\"Please redo.\"}"), max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("todo"));

        assertStaysTrue(() -> {
            try {
                assertThat(taskIds(issue)).isEmpty();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }
}
