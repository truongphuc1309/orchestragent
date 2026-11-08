package com.orchestragent.agent.application;

import com.orchestragent.agent.application.UsageReader.AgentTotals;
import com.orchestragent.agent.application.UsageReader.DayTotals;
import com.orchestragent.agent.application.UsageReader.Totals;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the agents of a workspace cost over the last days: runs, tokens and dollars in total, per agent, per issue
 * (the most expensive ones) and per day. Days are UTC days; the period is today and the days before it. Any member
 * may look.
 */
@Service
public class UsageService {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 365;
    public static final int TOP_ISSUES = 20;

    private final UsageReader reader;
    private final IssueDirectory issues;
    private final WorkspaceAccess access;
    private final Clock clock;

    UsageService(UsageReader reader, IssueDirectory issues, WorkspaceAccess access, Clock clock) {
        this.reader = reader;
        this.issues = issues;
        this.access = access;
        this.clock = clock;
    }

    /** One issue's runs. {@code key} ({@code WEB-12}) and {@code title} are null when the issue was deleted since. */
    public record IssueUsage(UUID issueId, String key, String title, Totals totals) {
    }

    /** {@code byDay} has one entry for every day of the period, with zeros for days without runs. */
    public record UsageReport(
            LocalDate firstDay,
            LocalDate lastDay,
            Totals totals,
            List<AgentTotals> byAgent,
            List<IssueUsage> topIssues,
            List<DayTotals> byDay) {
    }

    @Transactional(readOnly = true)
    public UsageReport usage(UUID userId, UUID workspaceId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new ValidationException("usage.invalid_days", "days must be between 1 and " + MAX_DAYS);
        }
        access.requireMember(workspaceId, userId);

        LocalDate lastDay = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate firstDay = lastDay.minusDays(days - 1L);
        Instant from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<IssueUsage> topIssues = reader.topIssues(workspaceId, from, TOP_ISSUES).stream()
                .map(issue -> {
                    Optional<IssueDetails> details = issues.find(workspaceId, issue.issueId());
                    return new IssueUsage(
                            issue.issueId(),
                            details.map(IssueDetails::key).orElse(null),
                            details.map(IssueDetails::title).orElse(null),
                            issue.totals());
                })
                .toList();

        return new UsageReport(
                firstDay,
                lastDay,
                reader.totals(workspaceId, from),
                reader.byAgent(workspaceId, from),
                topIssues,
                everyDay(firstDay, lastDay, reader.byDay(workspaceId, from)));
    }

    private static List<DayTotals> everyDay(LocalDate firstDay, LocalDate lastDay, List<DayTotals> withRuns) {
        Map<LocalDate, DayTotals> known = withRuns.stream().collect(Collectors.toMap(DayTotals::day, Function.identity()));
        List<DayTotals> all = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            all.add(known.getOrDefault(day, new DayTotals(day, Totals.NONE)));
        }
        return all;
    }
}
