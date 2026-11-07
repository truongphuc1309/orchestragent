package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.RuntimeStatus;
import java.time.Instant;
import java.util.UUID;

/** Read models returned by the use cases. */
public final class Views {

    private Views() {
    }

    public record RuntimeView(
            UUID id,
            UUID workspaceId,
            UUID ownerId,
            String ownerName,
            String deviceName,
            String provider,
            String providerVersion,
            RuntimeStatus status,
            Instant lastHeartbeatAt) {
    }

    /** The part of a runtime an agent shows: where it works and whether that place is reachable. */
    public record RuntimeSummary(UUID id, String deviceName, String provider, RuntimeStatus status) {
    }

    public record AgentView(
            UUID id,
            UUID workspaceId,
            String name,
            String instructions,
            String model,
            RuntimeSummary runtime,
            UUID createdBy,
            String createdByName,
            Instant archivedAt,
            Instant createdAt) {

        public boolean archived() {
            return archivedAt != null;
        }
    }
}
