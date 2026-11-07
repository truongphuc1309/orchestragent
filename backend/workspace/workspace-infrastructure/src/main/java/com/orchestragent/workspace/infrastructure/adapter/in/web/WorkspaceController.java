package com.orchestragent.workspace.infrastructure.adapter.in.web;

import com.orchestragent.platform.security.CurrentUser;
import com.orchestragent.workspace.application.WorkspaceService;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.CreateWorkspaceRequest;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.WorkspaceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController {

    private final WorkspaceService workspaces;

    WorkspaceController(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @PostMapping
    ResponseEntity<WorkspaceResponse> create(CurrentUser user, @Valid @RequestBody CreateWorkspaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkspaceResponse.from(workspaces.create(user.id(), request.name())));
    }

    /** The workspaces the caller belongs to. */
    @GetMapping
    List<WorkspaceResponse> mine(CurrentUser user) {
        return workspaces.listMine(user.id()).stream().map(WorkspaceResponse::from).toList();
    }

    @GetMapping("/{workspaceId}")
    WorkspaceResponse get(CurrentUser user, @PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(workspaces.get(user.id(), workspaceId));
    }
}
