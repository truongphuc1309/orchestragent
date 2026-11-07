package com.orchestragent.agent.application;

import com.orchestragent.agent.application.RuntimeService.ProviderInfo;
import com.orchestragent.agent.application.Views.RuntimeView;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.workspace.contract.WorkspaceDirectory;
import com.orchestragent.workspace.contract.WorkspaceRef;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The runtime side of the daemon API. Every method acts for the owner of the API token and only touches what
 * that user owns: a daemon can neither see nor affect another person's computers.
 */
@Service
public class DaemonService {

    private final WorkspaceDirectory workspaces;
    private final RuntimeService runtimes;
    private final RuntimeRepository runtimeRepository;
    private final TaskMaintenance maintenance;

    DaemonService(
            WorkspaceDirectory workspaces,
            RuntimeService runtimes,
            RuntimeRepository runtimeRepository,
            TaskMaintenance maintenance) {
        this.workspaces = workspaces;
        this.runtimes = runtimes;
        this.runtimeRepository = runtimeRepository;
        this.maintenance = maintenance;
    }

    /** The workspaces of the token's owner: where the daemon may register. */
    @Transactional(readOnly = true)
    public List<WorkspaceRef> workspaces(UUID userId) {
        return workspaces.listForUser(userId);
    }

    @Transactional
    public List<RuntimeView> register(
            UUID userId, UUID workspaceId, String daemonId, String deviceName, List<ProviderInfo> providers) {
        return runtimes.register(userId, workspaceId, daemonId, deviceName, providers);
    }

    /** Keeps the user's runtimes online. Returns the ones that were refreshed. */
    @Transactional
    public List<UUID> heartbeat(UUID userId, Collection<UUID> runtimeIds) {
        return runtimes.heartbeat(userId, runtimeIds);
    }

    /**
     * The daemon shuts down. Its runtimes go offline at once, and tasks still running on them fail, because
     * nobody is going to finish them.
     */
    @Transactional
    public void deregister(UUID userId, Collection<UUID> runtimeIds) {
        List<RuntimeId> owned = runtimeRepository.findOwnedBy(userId, runtimeIds.stream().map(RuntimeId::new).toList())
                .stream()
                .map(runtime -> runtime.id())
                .toList();
        runtimes.deregister(userId, runtimeIds);
        maintenance.failRunningTasksOf(owned);
    }
}
