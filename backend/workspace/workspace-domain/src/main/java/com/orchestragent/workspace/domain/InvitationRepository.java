package com.orchestragent.workspace.domain;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository {

    void save(Invitation invitation);

    Optional<Invitation> findById(InvitationId id);

    Optional<Invitation> findPending(WorkspaceId workspaceId, Email email);

    /** Pending invitations of a workspace, newest first. */
    List<Invitation> findPendingByWorkspace(WorkspaceId workspaceId);

    /** Pending invitations addressed to an email, newest first. */
    List<Invitation> findPendingByEmail(Email email);
}
