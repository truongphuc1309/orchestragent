package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.RuntimeService;
import com.orchestragent.agent.infrastructure.adapter.in.web.AgentDtos.RuntimeResponse;
import com.orchestragent.platform.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The runtimes of a workspace, read-only for the web app. Daemons register and keep them alive through the
 * daemon API (step 8), which authenticates with API tokens instead of the browser session.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/runtimes")
class RuntimeController {

    private final RuntimeService runtimes;

    RuntimeController(RuntimeService runtimes) {
        this.runtimes = runtimes;
    }

    @GetMapping
    List<RuntimeResponse> list(CurrentUser user, @PathVariable UUID workspaceId) {
        return runtimes.list(user.id(), workspaceId).stream().map(RuntimeResponse::from).toList();
    }
}
