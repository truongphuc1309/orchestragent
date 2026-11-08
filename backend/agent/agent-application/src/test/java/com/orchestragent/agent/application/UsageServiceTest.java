package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.TaskFakes.FakeIssues;
import com.orchestragent.agent.application.UsageReader.DayTotals;
import com.orchestragent.agent.application.UsageReader.IssueTotals;
import com.orchestragent.agent.application.UsageReader.Totals;
import com.orchestragent.agent.application.UsageService.UsageReport;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageServiceTest {

    private final UUID workspace = UUID.randomUUID();
    private final UUID mia = UUID.randomUUID();
    private final FakeAccess access = new FakeAccess();
    private final FakeIssues issues = new FakeIssues();
    private final MutableClock clock = new MutableClock();
    private final RecordingReader reader = new RecordingReader();
    private final UsageService service = new UsageService(reader, issues, access, clock);

    UsageServiceTest() {
        access.join(workspace, mia, WorkspaceRole.MEMBER);
    }

    /** Answers with what the test put in, and remembers the period it was asked about. */
    static final class RecordingReader implements UsageReader {
        Instant askedFrom;
        Totals totals = Totals.NONE;
        final List<IssueTotals> issues = new ArrayList<>();
        final List<DayTotals> days = new ArrayList<>();

        @Override
        public Totals totals(UUID workspaceId, Instant from) {
            askedFrom = from;
            return totals;
        }

        @Override
        public List<AgentTotals> byAgent(UUID workspaceId, Instant from) {
            return List.of();
        }

        @Override
        public List<IssueTotals> topIssues(UUID workspaceId, Instant from, int limit) {
            return issues;
        }

        @Override
        public List<DayTotals> byDay(UUID workspaceId, Instant from) {
            return days;
        }
    }

    private static Totals runs(long runs, String cost) {
        return new Totals(runs, runs, 0, runs * 100, runs * 10, new BigDecimal(cost));
    }

    @Test
    void thePeriodIsTodayAndTheDaysBeforeItInUtc() {
        // The clock says 2026-01-01T10:00:00Z.
        UsageReport report = service.usage(mia, workspace, 7);

        assertThat(report.firstDay()).isEqualTo(LocalDate.parse("2025-12-26"));
        assertThat(report.lastDay()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(reader.askedFrom).isEqualTo(Instant.parse("2025-12-26T00:00:00Z"));
    }

    @Test
    void everyDayOfThePeriodIsListedWithZerosForQuietDays() {
        reader.days.add(new DayTotals(LocalDate.parse("2025-12-31"), runs(2, "0.50")));

        UsageReport report = service.usage(mia, workspace, 3);

        assertThat(report.byDay()).extracting(DayTotals::day).containsExactly(
                LocalDate.parse("2025-12-30"), LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01"));
        assertThat(report.byDay()).extracting(day -> day.totals().runs()).containsExactly(0L, 2L, 0L);
    }

    @Test
    void topIssuesCarryTheirNumberAndTitleOrNothingWhenDeleted() {
        IssueDetails kept = issues.add(workspace, 7, null, null, "in_review");
        UUID deleted = UUID.randomUUID();
        reader.issues.add(new IssueTotals(kept.id(), runs(3, "1.20")));
        reader.issues.add(new IssueTotals(deleted, runs(1, "0.10")));

        UsageReport report = service.usage(mia, workspace, 30);

        assertThat(report.topIssues()).satisfiesExactly(
                first -> {
                    assertThat(first.key()).isEqualTo("WEB-7");
                    assertThat(first.title()).isEqualTo("Issue 7");
                    assertThat(first.totals().costUsd()).isEqualByComparingTo("1.20");
                },
                second -> {
                    assertThat(second.issueId()).isEqualTo(deleted);
                    assertThat(second.key()).isNull();
                    assertThat(second.title()).isNull();
                });
    }

    @Test
    void anIssueOfAnotherWorkspaceIsNotNamed() {
        IssueDetails elsewhere = issues.add(UUID.randomUUID(), 3, null, null, "todo");
        reader.issues.add(new IssueTotals(elsewhere.id(), runs(1, "0.10")));

        assertThat(service.usage(mia, workspace, 30).topIssues().getFirst().title()).isNull();
    }

    @Test
    void onlyMembersMayLook() {
        assertThatThrownBy(() -> service.usage(UUID.randomUUID(), workspace, 30)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void thePeriodIsOneDayToOneYear() {
        assertThatThrownBy(() -> service.usage(mia, workspace, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.usage(mia, workspace, 366)).isInstanceOf(ValidationException.class);
        assertThat(service.usage(mia, workspace, 365).byDay()).hasSize(365);
    }
}
