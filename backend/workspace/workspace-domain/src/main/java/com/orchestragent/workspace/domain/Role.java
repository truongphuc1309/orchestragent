package com.orchestragent.workspace.domain;

/** Role of a member inside a workspace. Ordered from most to least powerful. */
public enum Role {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean atLeast(Role other) {
        return ordinal() <= other.ordinal();
    }

    /** True when this role is strictly more powerful than {@code other}. */
    public boolean outranks(Role other) {
        return ordinal() < other.ordinal();
    }
}
