package com.orchestragent.workspace.contract;

/** What a member may do in a workspace. Ordered from most to least powerful: owner, admin, member. */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    /** True when this role is the same as or more powerful than {@code other}. */
    public boolean atLeast(WorkspaceRole other) {
        return ordinal() <= other.ordinal();
    }
}
