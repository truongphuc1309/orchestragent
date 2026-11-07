package com.orchestragent.agent.domain;

/**
 * Who may change an agent. An agent runs work on the computer of whoever owns its runtime, so changing it is
 * not open to everybody: the person who made it, and the admins and owners of the workspace, may edit,
 * archive and restore it. Everyone else can only use it (assign issues to it).
 */
public final class AgentPolicy {

    private AgentPolicy() {
    }

    public static boolean canManage(boolean isCreator, boolean isAdminOrOwner) {
        return isCreator || isAdminOrOwner;
    }
}
