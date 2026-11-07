package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One AI tool (provider) on one computer (daemon) that has registered with a workspace and can run agents.
 * It is online while its daemon keeps sending heartbeats.
 */
public final class Runtime {

    /** A runtime that has not sent a heartbeat for this long counts as gone. */
    public static final Duration OFFLINE_AFTER = Duration.ofSeconds(90);

    public static final int MAX_TEXT_LENGTH = 100;

    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9][a-z0-9._-]{0,49}");

    private final RuntimeId id;
    private final UUID workspaceId;
    private final UUID ownerUserId;
    private final String daemonId;
    private final String provider;
    private String deviceName;
    private String providerVersion;
    private RuntimeStatus status;
    private Instant lastHeartbeatAt;

    public Runtime(
            RuntimeId id,
            UUID workspaceId,
            UUID ownerUserId,
            String daemonId,
            String deviceName,
            String provider,
            String providerVersion,
            RuntimeStatus status,
            Instant lastHeartbeatAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.daemonId = Objects.requireNonNull(daemonId, "daemonId");
        this.deviceName = Objects.requireNonNull(deviceName, "deviceName");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.status = Objects.requireNonNull(status, "status");
        this.lastHeartbeatAt = Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
    }

    /** A first registration: the runtime starts online. */
    public static Runtime register(
            UUID workspaceId,
            UUID ownerUserId,
            String daemonId,
            String deviceName,
            String provider,
            String providerVersion,
            Instant now) {
        return new Runtime(
                RuntimeId.generate(),
                workspaceId,
                ownerUserId,
                validDaemonId(daemonId),
                validDeviceName(deviceName),
                validProvider(provider),
                validVersion(providerVersion),
                RuntimeStatus.ONLINE,
                now);
    }

    public static String validDaemonId(String raw) {
        return validText(raw, "runtime.invalid_daemon_id", "Daemon ID");
    }

    public static String validDeviceName(String raw) {
        return validText(raw, "runtime.invalid_device_name", "Device name");
    }

    /** Provider IDs are short lower-case codes such as {@code claude} or {@code mock}. */
    public static String validProvider(String raw) {
        String provider = raw == null ? "" : raw.trim();
        if (!PROVIDER.matcher(provider).matches()) {
            throw new ValidationException(
                    "runtime.invalid_provider", "Provider must be 1 to 50 lower-case letters, digits, dots, dashes or underscores");
        }
        return provider;
    }

    /** The version may be empty when the tool does not report one. */
    public static String validVersion(String raw) {
        String version = raw == null ? "" : raw.trim();
        if (version.length() > MAX_TEXT_LENGTH || version.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("runtime.invalid_version", "Version must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return version;
    }

    private static String validText(String raw, String code, String label) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH || text.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException(code, label + " must be 1 to " + MAX_TEXT_LENGTH + " characters on a single line");
        }
        return text;
    }

    /** The daemon registers again (after a restart): the runtime comes back online with what the daemon reports now. */
    public void reregister(String newDeviceName, String newVersion, Instant now) {
        deviceName = validDeviceName(newDeviceName);
        providerVersion = validVersion(newVersion);
        heartbeat(now);
    }

    /** A sign of life: the runtime is online and its heartbeat is up to date. */
    public void heartbeat(Instant now) {
        status = RuntimeStatus.ONLINE;
        lastHeartbeatAt = now;
    }

    /** The daemon shut down, or stopped answering. Returns whether the runtime was online before. */
    public boolean markOffline() {
        boolean wasOnline = status == RuntimeStatus.ONLINE;
        status = RuntimeStatus.OFFLINE;
        return wasOnline;
    }

    /** True when the runtime is online but has been silent for longer than {@link #OFFLINE_AFTER}. */
    public boolean isStale(Instant now) {
        return status == RuntimeStatus.ONLINE && lastHeartbeatAt.plus(OFFLINE_AFTER).isBefore(now);
    }

    public RuntimeId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID ownerUserId() {
        return ownerUserId;
    }

    public String daemonId() {
        return daemonId;
    }

    public String deviceName() {
        return deviceName;
    }

    public String provider() {
        return provider;
    }

    public String providerVersion() {
        return providerVersion;
    }

    public RuntimeStatus status() {
        return status;
    }

    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}
