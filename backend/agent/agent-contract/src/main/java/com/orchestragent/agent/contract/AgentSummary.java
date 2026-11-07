package com.orchestragent.agent.contract;

import java.util.UUID;

/** What other contexts may know about an agent. An archived agent still has a name but can no longer be assigned. */
public record AgentSummary(UUID id, UUID workspaceId, String name, boolean archived) {
}
