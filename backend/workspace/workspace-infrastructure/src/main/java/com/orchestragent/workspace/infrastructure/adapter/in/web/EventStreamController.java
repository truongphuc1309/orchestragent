package com.orchestragent.workspace.infrastructure.adapter.in.web;

import com.orchestragent.platform.security.CurrentUser;
import com.orchestragent.platform.sse.SseHub;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live event stream of a workspace (Server-Sent Events). Only members may listen; everyone else gets
 * the same "not found" as for any other workspace endpoint. The contexts publish their events to the
 * {@link SseHub}, which fans them out to the connections registered here.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/events")
class EventStreamController {

    private final WorkspaceAccess access;
    private final SseHub hub;

    EventStreamController(WorkspaceAccess access, SseHub hub) {
        this.access = access;
        this.hub = hub;
    }

    @GetMapping
    SseEmitter stream(CurrentUser user, @PathVariable UUID workspaceId) {
        access.requireMember(workspaceId, user.id());
        SseEmitter emitter = hub.register(workspaceId, user.id());
        try {
            // Without a first write the browser does not see the response headers until the first heartbeat,
            // so it would not know it is connected.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | RuntimeException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
