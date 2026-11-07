package com.orchestragent.workspace.application;

import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Role;

/** The domain and the contract each have their own role enum; this is the only place that maps between them. */
public final class Roles {

    private Roles() {
    }

    public static WorkspaceRole toContract(Role role) {
        return WorkspaceRole.valueOf(role.name());
    }

    public static Role toDomain(WorkspaceRole role) {
        return Role.valueOf(role.name());
    }
}
