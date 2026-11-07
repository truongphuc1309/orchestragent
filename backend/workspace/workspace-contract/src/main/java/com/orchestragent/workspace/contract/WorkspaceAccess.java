package com.orchestragent.workspace.contract;

import java.util.List;
import java.util.UUID;

/**
 * Membership checks for other contexts. Every workspace-scoped query in the system goes through this facade.
 * Becomes an HTTP client if the workspace context is split out.
 */
public interface WorkspaceAccess {

    /**
     * Succeeds silently for a member.
     *
     * @throws com.orchestragent.shared.error.NotFoundException when the user is not a member. A workspace the
     *         caller does not belong to looks exactly like one that does not exist.
     */
    void requireMember(UUID workspaceId, UUID userId);

    /**
     * Checks that the user is a member with at least the given role.
     *
     * @return the user's actual role
     * @throws com.orchestragent.shared.error.NotFoundException when the user is not a member
     * @throws com.orchestragent.shared.error.ForbiddenException when the user is a member with a lower role
     */
    WorkspaceRole requireRole(UUID workspaceId, UUID userId, WorkspaceRole minimum);

    boolean isMember(UUID workspaceId, UUID userId);

    List<UUID> listMemberIds(UUID workspaceId);
}
