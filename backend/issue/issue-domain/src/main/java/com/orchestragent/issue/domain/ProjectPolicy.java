package com.orchestragent.issue.domain;

/**
 * Who may change a project. Any member may create one; the person who made it, and the admins and owners of the
 * workspace, may rename, archive and restore it. Everyone else can only use it (create issues in it).
 */
public final class ProjectPolicy {

    private ProjectPolicy() {
    }

    public static boolean canManage(boolean isCreator, boolean isAdminOrOwner) {
        return isCreator || isAdminOrOwner;
    }
}
