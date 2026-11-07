package com.orchestragent.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IssueTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T11:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Issue newIssue() {
        return Issue.create(
                workspaceId, 7, "Fix login", "Details", Status.TODO, Priority.MEDIUM, null, Actor.member(userId), 1024, T0);
    }

    // ---- creation and validation -------------------------------------------------------------------------

    @Test
    void aNewIssueKeepsWhatItWasGiven() {
        Issue issue = newIssue();

        assertThat(issue.workspaceId()).isEqualTo(workspaceId);
        assertThat(issue.number()).isEqualTo(7);
        assertThat(issue.title()).isEqualTo("Fix login");
        assertThat(issue.description()).isEqualTo("Details");
        assertThat(issue.status()).isEqualTo(Status.TODO);
        assertThat(issue.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(issue.assignee()).isNull();
        assertThat(issue.creator()).isEqualTo(Actor.member(userId));
        assertThat(issue.position()).isEqualTo(1024);
        assertThat(issue.createdAt()).isEqualTo(T0);
        assertThat(issue.updatedAt()).isEqualTo(T0);
    }

    @Test
    void titleIsTrimmed() {
        Issue issue =
                Issue.create(workspaceId, 1, "  Padded  ", "", Status.BACKLOG, Priority.NONE, null, Actor.member(userId), 1, T0);

        assertThat(issue.title()).isEqualTo("Padded");
    }

    @Test
    void descriptionDefaultsToEmpty() {
        Issue issue =
                Issue.create(workspaceId, 1, "Title", null, Status.BACKLOG, Priority.NONE, null, Actor.member(userId), 1, T0);

        assertThat(issue.description()).isEmpty();
    }

    @Test
    void descriptionMayHaveSeveralLines() {
        assertThat(Issue.validDescription("line one\nline two")).isEqualTo("line one\nline two");
    }

    @Test
    void rejectsABlankOrTooLongTitle() {
        assertThatThrownBy(() -> Issue.validTitle("   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Issue.validTitle(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Issue.validTitle("x".repeat(201))).isInstanceOf(ValidationException.class);
        assertThat(Issue.validTitle("x".repeat(200))).hasSize(200);
    }

    @Test
    void rejectsATitleWithALineBreak() {
        assertThatThrownBy(() -> Issue.validTitle("first\nsecond"))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("issue.invalid_title");
    }

    @Test
    void rejectsATooLongDescription() {
        assertThatThrownBy(() -> Issue.validDescription("x".repeat(20_001)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("issue.invalid_description");
        assertThat(Issue.validDescription("x".repeat(20_000))).hasSize(20_000);
    }

    // ---- editing -----------------------------------------------------------------------------------------

    @Test
    void renamingChangesTheTitleAndTheUpdateTime() {
        Issue issue = newIssue();

        assertThat(issue.rename("New title", T1)).isTrue();

        assertThat(issue.title()).isEqualTo("New title");
        assertThat(issue.updatedAt()).isEqualTo(T1);
    }

    @Test
    void renamingToTheSameTitleChangesNothing() {
        Issue issue = newIssue();

        assertThat(issue.rename("  Fix login  ", T1)).isFalse();

        assertThat(issue.updatedAt()).isEqualTo(T0);
    }

    @Test
    void anInvalidRenameLeavesTheIssueUntouched() {
        Issue issue = newIssue();

        assertThatThrownBy(() -> issue.rename("", T1)).isInstanceOf(ValidationException.class);

        assertThat(issue.title()).isEqualTo("Fix login");
        assertThat(issue.updatedAt()).isEqualTo(T0);
    }

    @Test
    void describingReportsRealChangesOnly() {
        Issue issue = newIssue();

        assertThat(issue.describe("Details", T1)).isFalse();
        assertThat(issue.describe("More details", T1)).isTrue();
        assertThat(issue.description()).isEqualTo("More details");
        assertThat(issue.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changingPriorityReportsRealChangesOnly() {
        Issue issue = newIssue();

        assertThat(issue.changePriority(Priority.MEDIUM, T1)).isFalse();
        assertThat(issue.changePriority(Priority.URGENT, T1)).isTrue();
        assertThat(issue.priority()).isEqualTo(Priority.URGENT);
        assertThat(issue.updatedAt()).isEqualTo(T1);
    }

    @Test
    void assigningAndUnassigning() {
        Issue issue = newIssue();
        UUID assigneeId = UUID.randomUUID();

        assertThat(issue.assign(null, T1)).as("already unassigned").isFalse();
        assertThat(issue.assign(Actor.member(assigneeId), T1)).isTrue();
        assertThat(issue.assignee()).isEqualTo(Actor.member(assigneeId));
        assertThat(issue.assign(Actor.member(assigneeId), T1)).as("same assignee").isFalse();
        assertThat(issue.assign(Actor.agent(assigneeId), T1)).as("same ID but an agent").isTrue();
        assertThat(issue.assign(null, T1)).isTrue();
        assertThat(issue.assignee()).isNull();
    }

    // ---- status and position -----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Status.class)
    void anyStatusCanFollowAnyOther(Status target) {
        for (Status start : Status.values()) {
            Issue issue = Issue.create(
                    workspaceId, 1, "T", "", start, Priority.NONE, null, Actor.member(userId), 1024, T0);

            boolean changed = issue.moveTo(target, 2048, T1);

            assertThat(changed).isTrue();
            assertThat(issue.status()).isEqualTo(target);
            assertThat(issue.position()).isEqualTo(2048);
            assertThat(issue.updatedAt()).isEqualTo(T1);
        }
    }

    @Test
    void movingWithinTheSameColumnChangesOnlyThePosition() {
        Issue issue = newIssue();

        assertThat(issue.moveTo(Status.TODO, 1536, T1)).isTrue();

        assertThat(issue.status()).isEqualTo(Status.TODO);
        assertThat(issue.position()).isEqualTo(1536);
    }

    @Test
    void movingToTheSameSpotChangesNothing() {
        Issue issue = newIssue();

        assertThat(issue.moveTo(Status.TODO, 1024, T1)).isFalse();

        assertThat(issue.updatedAt()).isEqualTo(T0);
    }

    @Test
    void renumberingChangesThePositionButIsNotAnEdit() {
        Issue issue = newIssue();

        issue.renumber(5000);

        assertThat(issue.position()).isEqualTo(5000);
        assertThat(issue.updatedAt()).isEqualTo(T0);
    }

    @Test
    void onlyDoneAndCancelledCountAsClosed() {
        for (Status status : Status.values()) {
            assertThat(status.isClosed()).isEqualTo(status == Status.DONE || status == Status.CANCELLED);
        }
    }

    // ---- codes -------------------------------------------------------------------------------------------

    @Test
    void statusAndPriorityCodesRoundTripThroughLowerCaseNames() {
        for (Status status : Status.values()) {
            assertThat(Status.fromCode(status.code())).isEqualTo(status);
        }
        for (Priority priority : Priority.values()) {
            assertThat(Priority.fromCode(priority.code())).isEqualTo(priority);
        }
        assertThat(Status.IN_PROGRESS.code()).isEqualTo("in_progress");
        assertThat(Status.fromCode(" Done ")).isEqualTo(Status.DONE);
    }

    @Test
    void unknownCodesAreValidationErrors() {
        assertThatThrownBy(() -> Status.fromCode("finished")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Status.fromCode(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Priority.fromCode("critical")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ActorType.fromCode("robot")).isInstanceOf(ValidationException.class);
    }

    @Test
    void anActorCannotBeTheSystem() {
        assertThatThrownBy(() -> new Actor(ActorType.SYSTEM, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
