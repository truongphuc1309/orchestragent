package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.AgentService.AgentDetails;
import com.orchestragent.agent.application.Views.AgentView;
import com.orchestragent.agent.application.Views.RuntimeSummary;
import com.orchestragent.agent.application.Views.RuntimeView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Wire format of the agent and runtime REST API. Runtime status travels as "online" or "offline". */
final class AgentDtos {

    private AgentDtos() {
    }

    /** Used both to create an agent and to replace its editable fields. */
    record AgentRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 20_000) String instructions,
            @NotNull UUID runtimeId,
            @Size(max = 100) String model) {

        AgentDetails toDetails() {
            return new AgentDetails(name, instructions, runtimeId, model);
        }
    }

    record RuntimeSummaryResponse(UUID id, String deviceName, String provider, String status) {

        static RuntimeSummaryResponse from(RuntimeSummary summary) {
            return summary == null
                    ? null
                    : new RuntimeSummaryResponse(
                            summary.id(), summary.deviceName(), summary.provider(), summary.status().code());
        }
    }

    record AgentResponse(
            UUID id,
            String name,
            String instructions,
            String model,
            RuntimeSummaryResponse runtime,
            UUID createdById,
            String createdByName,
            boolean archived,
            Instant archivedAt,
            Instant createdAt) {

        static AgentResponse from(AgentView view) {
            return new AgentResponse(
                    view.id(),
                    view.name(),
                    view.instructions(),
                    view.model(),
                    RuntimeSummaryResponse.from(view.runtime()),
                    view.createdBy(),
                    view.createdByName(),
                    view.archived(),
                    view.archivedAt(),
                    view.createdAt());
        }
    }

    record RuntimeResponse(
            UUID id,
            UUID ownerId,
            String ownerName,
            String deviceName,
            String provider,
            String providerVersion,
            String status,
            Instant lastHeartbeatAt) {

        static RuntimeResponse from(RuntimeView view) {
            return new RuntimeResponse(
                    view.id(),
                    view.ownerId(),
                    view.ownerName(),
                    view.deviceName(),
                    view.provider(),
                    view.providerVersion(),
                    view.status().code(),
                    view.lastHeartbeatAt());
        }
    }
}
