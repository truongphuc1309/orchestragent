package com.orchestragent.issue.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.Views.ProjectView;
import com.orchestragent.issue.domain.Project;
import com.orchestragent.issue.domain.ProjectId;
import com.orchestragent.issue.domain.ProjectKey;
import com.orchestragent.issue.domain.ProjectPolicy;
import com.orchestragent.issue.domain.ProjectRepository;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, editing, archiving and restoring projects. Every method first checks that the caller belongs to the
 * workspace (a non-member gets "not found"). Any member creates projects; the creator, admins and owners change
 * them. A key is unique in its workspace for good, archived projects included, so an issue key never points at
 * two issues over time.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final WorkspaceAccess access;
    private final UserDirectory users;
    private final IssueEvents events;
    private final Clock clock;

    ProjectService(
            ProjectRepository projects, WorkspaceAccess access, UserDirectory users, IssueEvents events, Clock clock) {
        this.projects = projects;
        this.access = access;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** What a person fills in when creating a project. The key is trimmed and upper-cased. */
    public record CreateProject(String key, String name, String description) {
    }

    /** The editable part of a project; the key is not part of it. Null means "leave as it is". */
    public record UpdateProject(String name, String description) {
    }

    @Transactional
    public ProjectView create(UUID userId, UUID workspaceId, CreateProject command) {
        access.requireMember(workspaceId, userId);
        ProjectKey key = ProjectKey.parse(command.key());
        Project project = Project.create(workspaceId, key, command.name(), command.description(), userId, clock.instant());
        if (projects.keyExists(workspaceId, key)) {
            throw new ConflictException(
                    "project.key_taken", "Another project of this workspace already uses the key " + key);
        }
        projects.save(project);
        events.projectChanged(project);
        return view(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UUID userId, UUID workspaceId, boolean includeArchived) {
        access.requireMember(workspaceId, userId);
        return views(projects.list(workspaceId, includeArchived));
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID userId, UUID workspaceId, UUID projectId) {
        access.requireMember(workspaceId, userId);
        return view(load(workspaceId, projectId));
    }

    /** Looks a project up by its key, ignoring case ({@code web} finds {@code WEB}). */
    @Transactional(readOnly = true)
    public ProjectView getByKey(UUID userId, UUID workspaceId, String key) {
        access.requireMember(workspaceId, userId);
        String normalized = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        if (!ProjectKey.isValid(normalized)) {
            throw notFound();
        }
        return view(projects.findByKey(workspaceId, new ProjectKey(normalized)).orElseThrow(ProjectService::notFound));
    }

    @Transactional
    public ProjectView update(UUID userId, UUID workspaceId, UUID projectId, UpdateProject command) {
        Project project = loadManageable(userId, workspaceId, projectId);
        project.requireActive();
        // Both fields are checked before either is changed, so a refused update leaves the project as it was.
        if (command.name() != null) {
            Project.validName(command.name());
        }
        if (command.description() != null) {
            Project.validDescription(command.description());
        }
        Instant now = clock.instant();
        boolean changed = command.name() != null && project.rename(command.name(), now);
        changed |= command.description() != null && project.describe(command.description(), now);
        if (changed) {
            projects.save(project);
            events.projectChanged(project);
        }
        return view(project);
    }

    /** An archived project takes no new issues; its issues stay as they are and remain visible. */
    @Transactional
    public ProjectView archive(UUID userId, UUID workspaceId, UUID projectId) {
        Project project = loadManageable(userId, workspaceId, projectId);
        if (project.archive(clock.instant())) {
            projects.save(project);
            events.projectChanged(project);
        }
        return view(project);
    }

    @Transactional
    public ProjectView restore(UUID userId, UUID workspaceId, UUID projectId) {
        Project project = loadManageable(userId, workspaceId, projectId);
        if (project.restore(clock.instant())) {
            projects.save(project);
            events.projectChanged(project);
        }
        return view(project);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private Project loadManageable(UUID userId, UUID workspaceId, UUID projectId) {
        WorkspaceRole role = access.requireRole(workspaceId, userId, WorkspaceRole.MEMBER);
        Project project = load(workspaceId, projectId);
        if (!ProjectPolicy.canManage(project.createdBy().equals(userId), role.atLeast(WorkspaceRole.ADMIN))) {
            throw new ForbiddenException(
                    "project.manage_forbidden", "Only the creator of a project, an admin or an owner can change it");
        }
        return project;
    }

    private Project load(UUID workspaceId, UUID projectId) {
        return projects.find(workspaceId, new ProjectId(projectId)).orElseThrow(ProjectService::notFound);
    }

    private ProjectView view(Project project) {
        return views(List.of(project)).getFirst();
    }

    private List<ProjectView> views(List<Project> list) {
        Map<UUID, UserView> creators = users.findByIds(list.stream().map(Project::createdBy).distinct().toList());
        return list.stream()
                .map(project -> {
                    UserView creator = creators.get(project.createdBy());
                    return new ProjectView(
                            project.id().value(),
                            project.workspaceId(),
                            project.key().value(),
                            project.name(),
                            project.description(),
                            project.createdBy(),
                            creator == null ? "Unknown user" : creator.name(),
                            project.archivedAt(),
                            project.createdAt(),
                            project.updatedAt());
                })
                .toList();
    }

    static NotFoundException notFound() {
        return new NotFoundException("project.not_found", "Project not found");
    }
}
