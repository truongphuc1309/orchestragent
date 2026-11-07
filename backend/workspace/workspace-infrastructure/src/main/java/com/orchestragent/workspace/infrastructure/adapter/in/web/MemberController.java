package com.orchestragent.workspace.infrastructure.adapter.in.web;

import com.orchestragent.platform.security.CurrentUser;
import com.orchestragent.workspace.application.MemberService;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.ChangeRoleRequest;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.MemberResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
class MemberController {

    private final MemberService members;

    MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    List<MemberResponse> list(CurrentUser user, @PathVariable UUID workspaceId) {
        return members.list(user.id(), workspaceId).stream().map(MemberResponse::from).toList();
    }

    @PatchMapping("/{userId}")
    ResponseEntity<Void> changeRole(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        members.changeRole(user.id(), workspaceId, userId, request.parsedRole());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    ResponseEntity<Void> remove(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID userId) {
        members.remove(user.id(), workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
