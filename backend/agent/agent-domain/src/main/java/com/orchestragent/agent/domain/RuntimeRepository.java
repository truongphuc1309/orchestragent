package com.orchestragent.agent.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeRepository {

    /** Inserts a new runtime or updates an existing one. */
    void save(Runtime runtime);

    Optional<Runtime> find(UUID workspaceId, RuntimeId id);

    /** The runtime a daemon registered for one provider in a workspace, if any. */
    Optional<Runtime> findByDaemon(UUID workspaceId, String daemonId, String provider);

    /** Runtimes of a workspace, oldest registration first. */
    List<Runtime> findByWorkspace(UUID workspaceId);

    /** The runtimes with these IDs that belong to this user, whatever the workspace. */
    List<Runtime> findOwnedBy(UUID ownerUserId, Collection<RuntimeId> ids);

    /** Online runtimes whose last heartbeat is before the given time. */
    List<Runtime> findOnlineSilentSince(Instant cutoff);
}
