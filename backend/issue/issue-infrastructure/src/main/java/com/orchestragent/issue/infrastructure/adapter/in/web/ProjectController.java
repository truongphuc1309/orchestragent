package com.orchestragent.issue.infrastructure.adapter.in.web;

import com.orchestragent.issue.application.ProjectService;
import com.orchestragent.issue.application.ProjectService.CreateProject;
import com.orchestragent.issue.application.ProjectService.UpdateProject;
import com.orchestragent.issue.application.Views.ProjectView;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
class ProjectController {

    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    /** The key is checked by the domain (format and uniqueness), so its error codes are the domain's. */
    record CreateProjectRequest(
            @NotBlank @Size(max = 20) String key, @NotBlank @Size(max = 80) String name, @Size(max = 2_000) String description) {
    }

    /** Every field is optional; a missing field is left as it is. The key cannot be changed. */
    record UpdateProjectRequest(@Size(max = 80) String name, @Size(max = 2_000) String description) {
    }

    record ProjectResponse(
            UUID id,
            String key,
            String name,
            String description,
            UUID createdById,
            String createdByName,
            boolean archived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {

        static ProjectResponse from(ProjectView view) {
            return new ProjectResponse(
                    view.id(),
                    view.key(),
                    view.name(),
                    view.description(),
                    view.createdBy(),
                    view.createdByName(),
                    view.archived(),
                    view.archivedAt(),
                    view.createdAt(),
                    view.updatedAt());
        }
    }

    @GetMapping
    List<ProjectResponse> list(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return projects.list(user.id(), workspaceId, includeArchived).stream().map(ProjectResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(
            CurrentUser user, @PathVariable UUID workspaceId, @Valid @RequestBody CreateProjectRequest request) {
        ProjectView created = projects.create(
                user.id(), workspaceId, new CreateProject(request.key(), request.name(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(created));
    }

    @GetMapping("/{projectId}")
    ProjectResponse get(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        return ProjectResponse.from(projects.get(user.id(), workspaceId, projectId));
    }

    @GetMapping("/key/{key}")
    ProjectResponse getByKey(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable String key) {
        return ProjectResponse.from(projects.getByKey(user.id(), workspaceId, key));
    }

    @PatchMapping("/{projectId}")
    ProjectResponse update(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(projects.update(
                user.id(), workspaceId, projectId, new UpdateProject(request.name(), request.description())));
    }

    @PostMapping("/{projectId}/archive")
    ProjectResponse archive(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        return ProjectResponse.from(projects.archive(user.id(), workspaceId, projectId));
    }

    @PostMapping("/{projectId}/restore")
    ProjectResponse restore(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        return ProjectResponse.from(projects.restore(user.id(), workspaceId, projectId));
    }
}
