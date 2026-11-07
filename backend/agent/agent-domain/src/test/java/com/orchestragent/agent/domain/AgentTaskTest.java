package com.orchestragent.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentTaskTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);

    private AgentTask queued() {
        return AgentTask.queue(
                UUID.randomUUID(), UUID.randomUUID(), AgentId.generate(), RuntimeId.generate(),
                TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, T0);
    }

    private AgentTask dispatched() {
        AgentTask task = queued();
        task.dispatch(T0);
        return task;
    }

    private AgentTask running() {
        AgentTask task = dispatched();
        task.start(T1);
        return task;
    }

    // ---- the happy path ----------------------------------------------------------------------------------

    @Test
    void aNewTaskWaitsInTheQueue() {
        AgentTask task = queued();

        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.attempt()).isEqualTo(1);
        assertThat(task.createdAt()).isEqualTo(T0);
        assertThat(task.leaseExpiresAt()).isNull();
        assertThat(task.startedAt()).isNull();
        assertThat(task.finishedAt()).isNull();
        assertThat(task.usage()).isEqualTo(TaskUsage.NONE);
        assertThat(task.isActive()).isTrue();
    }

    @Test
    void dispatchingGivesTheDaemonSixtySecondsToStart() {
        AgentTask task = queued();

        task.dispatch(T0);

        assertThat(task.status()).isEqualTo(TaskStatus.DISPATCHED);
        assertThat(task.leaseExpiresAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void startingClearsTheLeaseAndNotesWhenWorkBegan() {
        AgentTask task = dispatched();

        assertThat(task.start(T1)).isTrue();

        assertThat(task.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.startedAt()).isEqualTo(T1);
        assertThat(task.leaseExpiresAt()).isNull();
    }

    @Test
    void completingKeepsTheOutputSessionFolderAndCost() {
        AgentTask task = running();

        boolean changed = task.complete("Done!", " session-1 ", "/work/1", new TaskUsage(100L, 50L, new BigDecimal("0.0123")), T2);

        assertThat(changed).isTrue();
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.output()).isEqualTo("Done!");
        assertThat(task.sessionId()).isEqualTo("session-1");
        assertThat(task.workDir()).isEqualTo("/work/1");
        assertThat(task.usage().inputTokens()).isEqualTo(100);
        assertThat(task.usage().outputTokens()).isEqualTo(50);
        assertThat(task.usage().costUsd()).isEqualByComparingTo("0.0123");
        assertThat(task.finishedAt()).isEqualTo(T2);
        assertThat(task.isActive()).isFalse();
    }

    @Test
    void blankSessionAndFolderAreNotKept() {
        AgentTask task = running();

        task.complete("Done", "  ", "", null, T2);

        assertThat(task.sessionId()).isNull();
        assertThat(task.workDir()).isNull();
        assertThat(task.usage()).isEqualTo(TaskUsage.NONE);
    }

    @Test
    void failingKeepsTheReasonAndTheExplanation() {
        AgentTask task = running();

        assertThat(task.fail("The tool crashed", "Timeout", "s1", "/work", T2)).isTrue();

        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.error()).isEqualTo("The tool crashed");
        assertThat(task.failureReason()).isEqualTo("timeout");
        assertThat(task.sessionId()).isEqualTo("s1");
        assertThat(task.finishedAt()).isEqualTo(T2);
    }

    @Test
    void aTaskCanFailRightAfterBeingClaimed() {
        AgentTask task = dispatched();

        assertThat(task.fail("Could not prepare the folder", "agent_error", null, null, T1)).isTrue();

        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.leaseExpiresAt()).isNull();
    }

    @Test
    void anUnusableFailureReasonBecomesTheDefault() {
        for (String bad : new String[] {null, "", "  ", "has space", "x".repeat(51), "semi;colon"}) {
            AgentTask task = running();
            task.fail("boom", bad, null, null, T2);
            assertThat(task.failureReason()).as(String.valueOf(bad)).isEqualTo("agent_error");
        }
    }

    @Test
    void aLeaseThatRanOutSendsTheTaskBackToTheQueue() {
        AgentTask task = dispatched();

        task.requeue();

        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.leaseExpiresAt()).isNull();
    }

    // ---- repeating a call is harmless --------------------------------------------------------------------

    @Test
    void startingARunningTaskAgainChangesNothing() {
        AgentTask task = running();

        assertThat(task.start(T2)).isFalse();

        assertThat(task.startedAt()).isEqualTo(T1);
    }

    @Test
    void completingACompletedTaskAgainChangesNothing() {
        AgentTask task = running();
        task.complete("First", "s1", null, null, T2);

        assertThat(task.complete("Second", "s2", null, null, T2.plusSeconds(5))).isFalse();

        assertThat(task.output()).isEqualTo("First");
        assertThat(task.sessionId()).isEqualTo("s1");
        assertThat(task.finishedAt()).isEqualTo(T2);
    }

    @Test
    void failingAFailedTaskAgainChangesNothing() {
        AgentTask task = running();
        task.fail("First", "timeout", null, null, T2);

        assertThat(task.fail("Second", "agent_error", null, null, T2.plusSeconds(5))).isFalse();

        assertThat(task.error()).isEqualTo("First");
        assertThat(task.failureReason()).isEqualTo("timeout");
    }

    // ---- illegal changes are refused ---------------------------------------------------------------------

    @Test
    void aQueuedTaskCannotBeStartedBecauseTheDaemonLostItsLease() {
        assertThatThrownBy(() -> queued().start(T1))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("task.lease_lost");
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    void aFinishedTaskCannotBeStartedDispatchedOrRequeued(TaskStatus finished) {
        AgentTask task = inState(finished);

        assertThatThrownBy(() -> task.start(T2)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> task.dispatch(T2)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(task::requeue).isInstanceOf(ConflictException.class);
        assertThat(task.status()).isEqualTo(finished);
    }

    @Test
    void onlyARunningTaskCanComplete() {
        assertThatThrownBy(() -> queued().complete("x", null, null, null, T1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> dispatched().complete("x", null, null, null, T1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> inState(TaskStatus.FAILED).complete("x", null, null, null, T2))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> inState(TaskStatus.CANCELLED).complete("x", null, null, null, T2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyAClaimedTaskCanFail() {
        assertThatThrownBy(() -> queued().fail("x", null, null, null, T1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> inState(TaskStatus.COMPLETED).fail("x", null, null, null, T2))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> inState(TaskStatus.CANCELLED).fail("x", null, null, null, T2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyAQueuedTaskCanBeDispatchedAndOnlyADispatchedOneRequeued() {
        assertThatThrownBy(() -> dispatched().dispatch(T1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> running().dispatch(T2)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> queued().requeue()).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> running().requeue()).isInstanceOf(ConflictException.class);
    }

    // ---- cancelling --------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"QUEUED", "DISPATCHED", "RUNNING"})
    void anyActiveTaskCanBeCancelled(TaskStatus active) {
        AgentTask task = inState(active);

        assertThat(task.cancel(T2)).isTrue();

        assertThat(task.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.finishedAt()).isEqualTo(T2);
        assertThat(task.leaseExpiresAt()).isNull();
        assertThat(task.isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    void aFinishedTaskCannotBeCancelledAndStaysAsItWas(TaskStatus finished) {
        AgentTask task = inState(finished);
        Instant before = task.finishedAt();

        assertThat(task.cancel(T2.plusSeconds(60))).isFalse();

        assertThat(task.status()).isEqualTo(finished);
        assertThat(task.finishedAt()).isEqualTo(before);
    }

    // ---- moving and limits -------------------------------------------------------------------------------

    @Test
    void aQueuedTaskFollowsItsAgentToAnotherRuntimeButARunningOneDoesNot() {
        AgentTask task = queued();
        RuntimeId other = RuntimeId.generate();

        task.moveToRuntime(other);

        assertThat(task.runtimeId()).isEqualTo(other);
        assertThatThrownBy(() -> running().moveToRuntime(other)).isInstanceOf(ConflictException.class);
    }

    @Test
    void overlongOutputAndErrorsAreCut() {
        AgentTask completed = running();
        completed.complete("x".repeat(AgentTask.MAX_OUTPUT_LENGTH + 500), null, null, null, T2);
        AgentTask failed = running();
        failed.fail("y".repeat(AgentTask.MAX_ERROR_LENGTH + 500), null, null, null, T2);

        assertThat(completed.output()).hasSize(AgentTask.MAX_OUTPUT_LENGTH).endsWith("(cut: too long)");
        assertThat(failed.error()).hasSize(AgentTask.MAX_ERROR_LENGTH).endsWith("(cut: too long)");
    }

    @Test
    void usageCannotBeNegative() {
        assertThatThrownBy(() -> new TaskUsage(-1L, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new TaskUsage(null, -1L, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new TaskUsage(null, null, new BigDecimal("-0.01"))).isInstanceOf(ValidationException.class);
    }

    @Test
    void statusAndTriggerCodesAreLowerCase() {
        assertThat(TaskStatus.DISPATCHED.code()).isEqualTo("dispatched");
        assertThat(TaskStatus.fromCode("running")).isEqualTo(TaskStatus.RUNNING);
        assertThat(TaskTrigger.CHANGES_REQUESTED.code()).isEqualTo("changes_requested");
        assertThat(TaskTrigger.fromCode("retry")).isEqualTo(TaskTrigger.RETRY);
        assertThat(MessageType.TOOL_USE.code()).isEqualTo("tool_use");
    }

    @Test
    void messageTypesAreParsedFromTheDaemonsWords() {
        assertThat(MessageType.fromCode(" Tool_Result ")).isEqualTo(MessageType.TOOL_RESULT);
        assertThatThrownBy(() -> MessageType.fromCode("shout")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MessageType.fromCode(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void messagesHaveNonNegativeNumbersAndCutOverlongContent() {
        assertThatThrownBy(() -> TaskMessage.received(-1, MessageType.TEXT, "x", T0)).isInstanceOf(ValidationException.class);

        TaskMessage cut = TaskMessage.received(0, MessageType.TOOL_RESULT, "z".repeat(TaskMessage.MAX_CONTENT_LENGTH + 10), T0);
        assertThat(cut.content()).hasSize(TaskMessage.MAX_CONTENT_LENGTH).endsWith("(cut: too long)");
        assertThat(TaskMessage.received(1, MessageType.TEXT, null, T0).content()).isEmpty();
    }

    private AgentTask inState(TaskStatus status) {
        return switch (status) {
            case QUEUED -> queued();
            case DISPATCHED -> dispatched();
            case RUNNING -> running();
            case COMPLETED -> {
                AgentTask task = running();
                task.complete("ok", null, null, null, T2);
                yield task;
            }
            case FAILED -> {
                AgentTask task = running();
                task.fail("boom", null, null, null, T2);
                yield task;
            }
            case CANCELLED -> {
                AgentTask task = running();
                task.cancel(T2);
                yield task;
            }
        };
    }
}
