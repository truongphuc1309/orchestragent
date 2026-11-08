package com.orchestragent.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T11:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Project newProject() {
        return Project.create(workspaceId, new ProjectKey("WEB"), "  Web app  ", "  The browser app  ", userId, T0);
    }

    @Test
    void aNewProjectIsActiveAndKeepsWhatItWasGiven() {
        Project project = newProject();

        assertThat(project.workspaceId()).isEqualTo(workspaceId);
        assertThat(project.key().value()).isEqualTo("WEB");
        assertThat(project.name()).isEqualTo("Web app");
        assertThat(project.description()).isEqualTo("The browser app");
        assertThat(project.createdBy()).isEqualTo(userId);
        assertThat(project.isArchived()).isFalse();
        assertThat(project.createdAt()).isEqualTo(T0);
        assertThat(project.updatedAt()).isEqualTo(T0);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Two\nlines"})
    void aNameIsRequiredAndOneLine(String name) {
        assertThatThrownBy(() -> Project.create(workspaceId, new ProjectKey("WEB"), name, "", userId, T0))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "project.invalid_name");
    }

    @Test
    void namesAndDescriptionsHaveALimit() {
        assertThat(Project.validName("x".repeat(Project.MAX_NAME_LENGTH))).hasSize(Project.MAX_NAME_LENGTH);
        assertThatThrownBy(() -> Project.validName("x".repeat(Project.MAX_NAME_LENGTH + 1)))
                .isInstanceOf(ValidationException.class);
        assertThat(Project.validDescription(null)).isEmpty();
        assertThatThrownBy(() -> Project.validDescription("x".repeat(Project.MAX_DESCRIPTION_LENGTH + 1)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "project.invalid_description");
    }

    @Test
    void renamingAndDescribingSayWhetherAnythingChanged() {
        Project project = newProject();

        assertThat(project.rename(" Web app ", T1)).isFalse();
        assertThat(project.updatedAt()).isEqualTo(T0);
        assertThat(project.rename("Website", T1)).isTrue();
        assertThat(project.describe("Pages", T1)).isTrue();
        assertThat(project.name()).isEqualTo("Website");
        assertThat(project.description()).isEqualTo("Pages");
        assertThat(project.updatedAt()).isEqualTo(T1);
    }

    @Test
    void archivingAndRestoringAreIdempotent() {
        Project project = newProject();

        assertThat(project.archive(T1)).isTrue();
        assertThat(project.isArchived()).isTrue();
        assertThat(project.archivedAt()).isEqualTo(T1);
        assertThat(project.archive(T1)).isFalse();

        assertThat(project.restore(T1)).isTrue();
        assertThat(project.isArchived()).isFalse();
        assertThat(project.restore(T1)).isFalse();
    }

    @Test
    void anArchivedProjectCannotBeEdited() {
        Project project = newProject();
        project.archive(T1);

        assertThatThrownBy(() -> project.rename("Other", T1))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "project.archived");
        assertThatThrownBy(() -> project.describe("Other", T1)).isInstanceOf(ConflictException.class);
        assertThat(project.name()).isEqualTo("Web app");
    }

    @Test
    void theCreatorAdminsAndOwnersManageAProject() {
        // isCreator, isAdminOrOwner -> may manage
        assertThat(ProjectPolicy.canManage(true, false)).isTrue();
        assertThat(ProjectPolicy.canManage(false, true)).isTrue();
        assertThat(ProjectPolicy.canManage(true, true)).isTrue();
        assertThat(ProjectPolicy.canManage(false, false)).isFalse();
    }
}
