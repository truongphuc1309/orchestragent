package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.AgentService;
import com.orchestragent.agent.infrastructure.adapter.in.web.AgentDtos.AgentRequest;
import com.orchestragent.agent.infrastructure.adapter.in.web.AgentDtos.AgentResponse;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents")
class AgentController {

    private final AgentService agents;

    AgentController(AgentService agents) {
        this.agents = agents;
    }

    @GetMapping
    List<AgentResponse> list(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return agents.list(user.id(), workspaceId, includeArchived).stream().map(AgentResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<AgentResponse> create(
            CurrentUser user, @PathVariable UUID workspaceId, @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AgentResponse.from(agents.create(user.id(), workspaceId, request.toDetails())));
    }

    @GetMapping("/{agentId}")
    AgentResponse get(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID agentId) {
        return AgentResponse.from(agents.get(user.id(), workspaceId, agentId));
    }

    @PutMapping("/{agentId}")
    AgentResponse update(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID agentId,
            @Valid @RequestBody AgentRequest request) {
        return AgentResponse.from(agents.update(user.id(), workspaceId, agentId, request.toDetails()));
    }

    @PostMapping("/{agentId}/archive")
    AgentResponse archive(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID agentId) {
        return AgentResponse.from(agents.archive(user.id(), workspaceId, agentId));
    }

    @PostMapping("/{agentId}/restore")
    AgentResponse restore(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID agentId) {
        return AgentResponse.from(agents.restore(user.id(), workspaceId, agentId));
    }
}
