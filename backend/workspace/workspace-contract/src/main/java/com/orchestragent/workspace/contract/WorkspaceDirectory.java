package com.orchestragent.workspace.contract;

import java.util.List;
import java.util.UUID;

/** Which workspaces a user belongs to, for other contexts (the daemon API lists them). */
public interface WorkspaceDirectory {

    /** The workspaces the user is a member of, oldest first. */
    List<WorkspaceRef> listForUser(UUID userId);
}
