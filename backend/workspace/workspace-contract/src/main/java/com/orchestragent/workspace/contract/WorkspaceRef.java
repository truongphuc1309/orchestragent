package com.orchestragent.workspace.contract;

import java.util.UUID;

/** What other contexts may know about a workspace. */
public record WorkspaceRef(UUID id, String name, String slug) {
}
