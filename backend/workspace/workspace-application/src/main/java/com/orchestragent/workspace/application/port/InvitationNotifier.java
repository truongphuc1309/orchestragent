package com.orchestragent.workspace.application.port;

import java.util.UUID;

/** Tells an invited person by email that they were invited. */
public interface InvitationNotifier {

    void invited(Notice notice);

    record Notice(String recipientEmail, String workspaceName, String inviterName, UUID invitationId, String role) {
    }
}
