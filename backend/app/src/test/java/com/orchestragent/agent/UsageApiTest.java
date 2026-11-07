package com.orchestragent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The usage numbers come from the runs the daemon reported, summed by Postgres. */
class UsageApiTest extends TaskWorkflowTestBase {

    private String usageBase;

    @BeforeEach
    void setUp() throws Exception {
        setUpWorld();
        usageBase = "/api/workspaces/" + workspaceId + "/usage";
    }

    private String runTo(String issueId, boolean succeed) throws Exception {
        assign(issueId, agentId);
        awaitTaskCount(issueId, 1);
        String taskId = claimedTaskIds(claim(miaToken, 5, runtimeId)).getFirst();
        start(miaToken, taskId);
        // complete() reports 100 input tokens, 40 output tokens and $0.0123; fail() reports no usage.
        return succeed ? complete(miaToken, taskId, "Done", "s") : fail(miaToken, taskId, "Broke", "agent_error");
    }

    @Test
    void sumsTheRunsInTotalPerAgentPerIssueAndPerDay() throws Exception {
        String first = createIssue("First");
        String second = createIssue("Second");
        String third = createIssue("Third");
        runTo(first, true);
        runTo(second, true);
        runTo(third, false);

        String body = mvc.perform(get(usageBase + "?days=7").cookie(max))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(body, "$.totals.runs")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(body, "$.totals.completed")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.totals.failed")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.totals.inputTokens")).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(body, "$.totals.outputTokens")).isEqualTo(80);
        Object cost = JsonPath.read(body, "$.totals.costUsd");
        assertThat(new BigDecimal(cost.toString())).isEqualByComparingTo("0.0246");

        assertThat(JsonPath.<List<String>>read(body, "$.byAgent[*].agentId")).containsExactly(agentId);
        assertThat(JsonPath.<Integer>read(body, "$.byAgent[0].totals.runs")).isEqualTo(3);

        assertThat(JsonPath.<List<String>>read(body, "$.topIssues[*].title")).containsExactlyInAnyOrder("First", "Second", "Third");
        assertThat(JsonPath.<String>read(body, "$.topIssues[2].title")).isEqualTo("Third");

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(JsonPath.<List<String>>read(body, "$.byDay[*].day")).hasSize(7).last().isEqualTo(today);
        assertThat(JsonPath.<Integer>read(body, "$.byDay[6].totals.runs")).isEqualTo(3);
        assertThat(JsonPath.<String>read(body, "$.lastDay")).isEqualTo(today);
    }

    @Test
    void anEmptyWorkspaceHasZeros() throws Exception {
        String body = mvc.perform(get(usageBase).cookie(olivia))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(body, "$.totals.runs")).isZero();
        assertThat(JsonPath.<List<Object>>read(body, "$.byDay")).hasSize(30);
        assertThat(JsonPath.<List<Object>>read(body, "$.byAgent")).isEmpty();
    }

    @Test
    void onlyMembersSeeItAndThePeriodIsChecked() throws Exception {
        mvc.perform(get(usageBase).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(usageBase + "?days=0").cookie(mia)).andExpect(status().isBadRequest());
        mvc.perform(get(usageBase + "?days=abc").cookie(mia)).andExpect(status().isBadRequest());
    }
}
