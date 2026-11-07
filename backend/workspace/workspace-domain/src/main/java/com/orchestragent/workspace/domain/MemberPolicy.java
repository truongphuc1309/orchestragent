package com.orchestragent.workspace.domain;

/**
 * Who may do what to whom. One rule underlies all of it: an owner can manage anyone; an admin can manage
 * members only; a member cannot manage anyone (except leaving).
 */
public final class MemberPolicy {

    private MemberPolicy() {
    }

    /** Owners can invite at any role; admins can invite admins and members. */
    public static boolean canInvite(Role actor, Role invitedRole) {
        return actor.atLeast(Role.ADMIN) && (actor == Role.OWNER || invitedRole != Role.OWNER);
    }

    /** Owners can set any role on anyone; admins can set member or admin on members. */
    public static boolean canChangeRole(Role actor, Role targetCurrent, Role newRole) {
        if (!actor.atLeast(Role.ADMIN)) {
            return false;
        }
        return actor == Role.OWNER || (actor.outranks(targetCurrent) && newRole != Role.OWNER);
    }

    /** Anyone may remove themselves; otherwise owners remove anyone and admins remove members. */
    public static boolean canRemove(Role actor, Role target, boolean removingSelf) {
        if (removingSelf) {
            return true;
        }
        return actor.atLeast(Role.ADMIN) && (actor == Role.OWNER || actor.outranks(target));
    }

    /** Only admins and owners see who has been invited and may withdraw invitations. */
    public static boolean canManageInvitations(Role actor) {
        return actor.atLeast(Role.ADMIN);
    }
}
