package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "agent", name = "runtimes")
class RuntimeEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "daemon_id", nullable = false)
    private String daemonId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_version", nullable = false)
    private String providerVersion;

    /** Lower-case: online or offline. */
    @Column(nullable = false)
    private String status;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    protected RuntimeEntity() {
    }

    RuntimeEntity(
            UUID id,
            UUID workspaceId,
            UUID ownerUserId,
            String daemonId,
            String deviceName,
            String provider,
            String providerVersion,
            String status,
            Instant lastHeartbeatAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.ownerUserId = ownerUserId;
        this.daemonId = daemonId;
        this.deviceName = deviceName;
        this.provider = provider;
        this.providerVersion = providerVersion;
        this.status = status;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    /** Copies the fields that can change after registration. */
    void update(String deviceName, String providerVersion, String status, Instant lastHeartbeatAt) {
        this.deviceName = deviceName;
        this.providerVersion = providerVersion;
        this.status = status;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    String getDaemonId() {
        return daemonId;
    }

    String getDeviceName() {
        return deviceName;
    }

    String getProvider() {
        return provider;
    }

    String getProviderVersion() {
        return providerVersion;
    }

    String getStatus() {
        return status;
    }

    Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}
