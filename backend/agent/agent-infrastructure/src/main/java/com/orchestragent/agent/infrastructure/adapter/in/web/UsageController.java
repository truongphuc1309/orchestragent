package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.UsageReader.Totals;
import com.orchestragent.agent.application.UsageService;
import com.orchestragent.agent.application.UsageService.UsageReport;
import com.orchestragent.platform.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/workspaces/{id}/usage?days=30}: what the agents cost, for the usage page. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/usage")
class UsageController {

    private final UsageService usage;

    UsageController(UsageService usage) {
        this.usage = usage;
    }

    record TotalsResponse(long runs, long completed, long failed, long inputTokens, long outputTokens, BigDecimal costUsd) {

        static TotalsResponse from(Totals totals) {
            return new TotalsResponse(totals.runs(), totals.completed(), totals.failed(), totals.inputTokens(),
                    totals.outputTokens(), totals.costUsd());
        }
    }

    record AgentResponse(UUID agentId, String agentName, boolean archived, TotalsResponse totals) {
    }

    record IssueResponse(UUID issueId, Integer number, String title, TotalsResponse totals) {
    }

    /** {@code day} is an ISO date (UTC). */
    record DayResponse(String day, TotalsResponse totals) {
    }

    record UsageResponse(
            String firstDay,
            String lastDay,
            TotalsResponse totals,
            List<AgentResponse> byAgent,
            List<IssueResponse> topIssues,
            List<DayResponse> byDay) {

        static UsageResponse from(UsageReport report) {
            return new UsageResponse(
                    report.firstDay().toString(),
                    report.lastDay().toString(),
                    TotalsResponse.from(report.totals()),
                    report.byAgent().stream()
                            .map(a -> new AgentResponse(a.agentId(), a.agentName(), a.archived(), TotalsResponse.from(a.totals())))
                            .toList(),
                    report.topIssues().stream()
                            .map(i -> new IssueResponse(i.issueId(), i.number(), i.title(), TotalsResponse.from(i.totals())))
                            .toList(),
                    report.byDay().stream()
                            .map(d -> new DayResponse(d.day().toString(), TotalsResponse.from(d.totals())))
                            .toList());
        }
    }

    @GetMapping
    UsageResponse usage(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "" + UsageService.DEFAULT_DAYS) int days) {
        return UsageResponse.from(usage.usage(user.id(), workspaceId, days));
    }
}
