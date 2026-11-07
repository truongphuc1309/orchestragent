package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "agent", name = "agents")
class AgentEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String instructions;

    @Column(name = "runtime_id", nullable = false)
    private UUID runtimeId;

    private String model;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentEntity() {
    }

    AgentEntity(
            UUID id,
            UUID workspaceId,
            String name,
            String instructions,
            UUID runtimeId,
            String model,
            UUID createdBy,
            Instant archivedAt,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.instructions = instructions;
        this.runtimeId = runtimeId;
        this.model = model;
        this.createdBy = createdBy;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
    }

    /** Copies the fields that can change after creation. */
    void update(String name, String instructions, UUID runtimeId, String model, Instant archivedAt) {
        this.name = name;
        this.instructions = instructions;
        this.runtimeId = runtimeId;
        this.model = model;
        this.archivedAt = archivedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getName() {
        return name;
    }

    String getInstructions() {
        return instructions;
    }

    UUID getRuntimeId() {
        return runtimeId;
    }

    String getModel() {
        return model;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
