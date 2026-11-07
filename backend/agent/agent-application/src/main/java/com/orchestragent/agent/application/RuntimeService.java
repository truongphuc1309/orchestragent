package com.orchestragent.agent.application;

import com.orchestragent.agent.application.Views.RuntimeView;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The runtimes (computers with an AI tool) that daemons register. The daemon API of step 8 calls register,
 * heartbeat and deregister on behalf of the owner of the API token; the web app lists them.
 */
@Service
public class RuntimeService {

    /** More providers than this in one registration is a mistake, not a real computer. */
    static final int MAX_PROVIDERS = 20;

    private final RuntimeRepository runtimes;
    private final WorkspaceAccess access;
    private final UserDirectory users;
    private final Clock clock;

    RuntimeService(RuntimeRepository runtimes, WorkspaceAccess access, UserDirectory users, Clock clock) {
        this.runtimes = runtimes;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** One AI tool found on the computer, with its version (may be empty). */
    public record ProviderInfo(String provider, String version) {
    }

    /**
     * Registers the tools of one computer in a workspace, creating a runtime for each or bringing an existing
     * one back online. The user must be a member of the workspace, and a computer ID that another user has
     * already registered cannot be taken over.
     */
    @Transactional
    public List<RuntimeView> register(
            UUID userId, UUID workspaceId, String daemonId, String deviceName, List<ProviderInfo> providers) {
        access.requireMember(workspaceId, userId);
        String cleanDaemonId = Runtime.validDaemonId(daemonId);
        String cleanDevice = Runtime.validDeviceName(deviceName);
        if (providers == null || providers.isEmpty() || providers.size() > MAX_PROVIDERS) {
            throw new ValidationException(
                    "runtime.invalid_providers", "Register between 1 and " + MAX_PROVIDERS + " providers");
        }

        Instant now = clock.instant();
        Map<String, Runtime> registered = new LinkedHashMap<>();
        for (ProviderInfo info : providers) {
            String provider = Runtime.validProvider(info.provider());
            if (registered.containsKey(provider)) {
                continue;
            }
            Runtime runtime = runtimes.findByDaemon(workspaceId, cleanDaemonId, provider)
                    .map(existing -> reclaim(existing, userId, cleanDevice, info.version(), now))
                    .orElseGet(() ->
                            Runtime.register(workspaceId, userId, cleanDaemonId, cleanDevice, provider, info.version(), now));
            runtimes.save(runtime);
            registered.put(provider, runtime);
        }
        return views(registered.values().stream().toList());
    }

    /**
     * Refreshes the heartbeat of the user's own runtimes. IDs that do not exist or belong to someone else are
     * ignored, so a daemon cannot keep another person's runtime alive or learn that it exists.
     *
     * @return the IDs that were refreshed
     */
    @Transactional
    public List<UUID> heartbeat(UUID userId, Collection<UUID> runtimeIds) {
        Instant now = clock.instant();
        List<UUID> refreshed = new ArrayList<>();
        for (Runtime runtime : runtimes.findOwnedBy(userId, ids(runtimeIds))) {
            runtime.heartbeat(now);
            runtimes.save(runtime);
            refreshed.add(runtime.id().value());
        }
        return refreshed;
    }

    /** The daemon is shutting down: its runtimes (only the user's own) go offline at once. */
    @Transactional
    public void deregister(UUID userId, Collection<UUID> runtimeIds) {
        for (Runtime runtime : runtimes.findOwnedBy(userId, ids(runtimeIds))) {
            if (runtime.markOffline()) {
                runtimes.save(runtime);
            }
        }
    }

    /**
     * Marks every runtime that has been silent for more than 90 seconds as offline. Run every 30 seconds.
     *
     * @return how many runtimes went offline
     */
    @Transactional
    public int markStaleOffline() {
        Instant cutoff = clock.instant().minus(Runtime.OFFLINE_AFTER);
        int count = 0;
        for (Runtime runtime : runtimes.findOnlineSilentSince(cutoff)) {
            if (runtime.markOffline()) {
                runtimes.save(runtime);
                count++;
            }
        }
        return count;
    }

    /** The runtimes of a workspace, for every member to see. */
    @Transactional(readOnly = true)
    public List<RuntimeView> list(UUID userId, UUID workspaceId) {
        access.requireMember(workspaceId, userId);
        return views(runtimes.findByWorkspace(workspaceId));
    }

    /** The same computer registers again: only its owner may, and it comes back online with what it reports now. */
    private static Runtime reclaim(Runtime existing, UUID userId, String deviceName, String version, Instant now) {
        if (!existing.ownerUserId().equals(userId)) {
            throw new ConflictException(
                    "runtime.owned_by_other", "This computer is already registered by another user");
        }
        existing.reregister(deviceName, version, now);
        return existing;
    }

    private static List<RuntimeId> ids(Collection<UUID> runtimeIds) {
        return runtimeIds.stream().map(RuntimeId::new).toList();
    }

    private List<RuntimeView> views(List<Runtime> list) {
        Map<UUID, UserView> owners = users.findByIds(list.stream().map(Runtime::ownerUserId).distinct().toList());
        return list.stream().map(runtime -> view(runtime, owners.get(runtime.ownerUserId()))).toList();
    }

    private static RuntimeView view(Runtime runtime, UserView owner) {
        return new RuntimeView(
                runtime.id().value(),
                runtime.workspaceId(),
                runtime.ownerUserId(),
                owner == null ? "Unknown user" : owner.name(),
                runtime.deviceName(),
                runtime.provider(),
                runtime.providerVersion(),
                runtime.status(),
                runtime.lastHeartbeatAt());
    }
}
