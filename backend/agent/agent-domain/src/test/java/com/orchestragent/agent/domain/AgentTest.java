package com.orchestragent.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T10:00:00Z");

    private final UUID workspace = UUID.randomUUID();
    private final UUID creator = UUID.randomUUID();
    private final RuntimeId runtime = RuntimeId.generate();

    private Agent newAgent() {
        return Agent.create(workspace, "Builder", "Write clean code.", runtime, "sonnet", creator, T0);
    }

    // ---- creation and validation -------------------------------------------------------------------------

    @Test
    void aNewAgentIsActiveAndKeepsWhatItWasGiven() {
        Agent agent = newAgent();

        assertThat(agent.name()).isEqualTo("Builder");
        assertThat(agent.instructions()).isEqualTo("Write clean code.");
        assertThat(agent.runtimeId()).isEqualTo(runtime);
        assertThat(agent.model()).isEqualTo("sonnet");
        assertThat(agent.createdBy()).isEqualTo(creator);
        assertThat(agent.createdAt()).isEqualTo(T0);
        assertThat(agent.workspaceId()).isEqualTo(workspace);
        assertThat(agent.isArchived()).isFalse();
        assertThat(agent.archivedAt()).isNull();
    }

    @Test
    void nameIsTrimmedAndMustBeASingleLine() {
        assertThat(Agent.validName("  Builder  ")).isEqualTo("Builder");
        assertThat(Agent.validName("x".repeat(60))).hasSize(60);
        for (String bad : new String[] {"", "   ", "x".repeat(61), "two\nlines"}) {
            assertThatThrownBy(() -> Agent.validName(bad)).as(bad).isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() -> Agent.validName(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void instructionsMayBeEmptyAndMultiLineButLimited() {
        assertThat(Agent.validInstructions(null)).isEmpty();
        assertThat(Agent.validInstructions("# Rules\n\n- be kind\n")).isEqualTo("# Rules\n\n- be kind");
        assertThat(Agent.validInstructions("x".repeat(20_000))).hasSize(20_000);
        assertThatThrownBy(() -> Agent.validInstructions("x".repeat(20_001)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("agent.invalid_instructions");
    }

    @Test
    void aBlankModelMeansTheToolDefault() {
        assertThat(Agent.validModel(null)).isNull();
        assertThat(Agent.validModel("   ")).isNull();
        assertThat(Agent.validModel(" opus ")).isEqualTo("opus");
        assertThatThrownBy(() -> Agent.validModel("x".repeat(101))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Agent.validModel("a\nb")).isInstanceOf(ValidationException.class);
    }

    // ---- editing -----------------------------------------------------------------------------------------

    @Test
    void editsReportRealChangesOnly() {
        Agent agent = newAgent();

        assertThat(agent.rename("Builder")).isFalse();
        assertThat(agent.rename(" Reviewer ")).isTrue();
        assertThat(agent.name()).isEqualTo("Reviewer");

        assertThat(agent.changeInstructions("Write clean code.")).isFalse();
        assertThat(agent.changeInstructions("Review carefully.")).isTrue();
        assertThat(agent.instructions()).isEqualTo("Review carefully.");

        assertThat(agent.changeModel("sonnet")).isFalse();
        assertThat(agent.changeModel("")).isTrue();
        assertThat(agent.model()).isNull();
        assertThat(agent.changeModel(null)).isFalse();

        assertThat(agent.changeRuntime(runtime)).isFalse();
        RuntimeId other = RuntimeId.generate();
        assertThat(agent.changeRuntime(other)).isTrue();
        assertThat(agent.runtimeId()).isEqualTo(other);
    }

    @Test
    void anInvalidEditLeavesTheAgentUntouched() {
        Agent agent = newAgent();

        assertThatThrownBy(() -> agent.rename("  ")).isInstanceOf(ValidationException.class);

        assertThat(agent.name()).isEqualTo("Builder");
    }

    // ---- archiving ---------------------------------------------------------------------------------------

    @Test
    void archivingAndRestoring() {
        Agent agent = newAgent();

        assertThat(agent.archive(T1)).isTrue();
        assertThat(agent.isArchived()).isTrue();
        assertThat(agent.archivedAt()).isEqualTo(T1);
        assertThat(agent.archive(T1.plusSeconds(60))).as("already archived").isFalse();
        assertThat(agent.archivedAt()).as("the first time stays").isEqualTo(T1);

        assertThat(agent.restore()).isTrue();
        assertThat(agent.isArchived()).isFalse();
        assertThat(agent.restore()).as("already active").isFalse();
    }

    @Test
    void anArchivedAgentCannotBeEdited() {
        Agent agent = newAgent();
        agent.archive(T1);

        assertThatThrownBy(() -> agent.rename("New")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> agent.changeInstructions("New")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> agent.changeModel("opus")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> agent.changeRuntime(RuntimeId.generate()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("agent.archived");
        assertThat(agent.name()).isEqualTo("Builder");
    }

    @Test
    void aRestoredAgentCanBeEditedAgain() {
        Agent agent = newAgent();
        agent.archive(T1);
        agent.restore();

        assertThat(agent.rename("Back")).isTrue();
    }
}
