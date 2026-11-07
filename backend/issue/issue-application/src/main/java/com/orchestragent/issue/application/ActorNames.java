package com.orchestragent.issue.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.Views.ActorView;
import com.orchestragent.issue.application.port.AgentDirectory;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Turns actors (type and ID) into views with display names, looking each kind up in its own context. */
@Component
class ActorNames {

    private static final String UNKNOWN_USER = "Unknown user";
    private static final String UNKNOWN_AGENT = "Unknown agent";
    private static final String SYSTEM = "System";

    private final UserDirectory users;
    private final AgentDirectory agents;

    ActorNames(UserDirectory users, AgentDirectory agents) {
        this.users = users;
        this.agents = agents;
    }

    /** Names for all the given actors with two lookups at most. Null entries (no actor) are skipped. */
    Lookup resolve(UUID workspaceId, Collection<Actor> actors) {
        Set<UUID> memberIds = new HashSet<>();
        Set<UUID> agentIds = new HashSet<>();
        for (Actor actor : actors) {
            if (actor == null) {
                continue;
            }
            (actor.type() == ActorType.MEMBER ? memberIds : agentIds).add(actor.id());
        }
        Map<UUID, String> names = new HashMap<>();
        if (!memberIds.isEmpty()) {
            Map<UUID, UserView> found = users.findByIds(memberIds);
            memberIds.forEach(id -> names.put(id, found.containsKey(id) ? found.get(id).name() : UNKNOWN_USER));
        }
        Map<UUID, String> agentNames = agentIds.isEmpty() ? Map.of() : agents.names(workspaceId, agentIds);
        return new Lookup(names, agentNames);
    }

    /** The result of {@link #resolve}: a view for any actor that was asked about. */
    static final class Lookup {

        private final Map<UUID, String> memberNames;
        private final Map<UUID, String> agentNames;

        private Lookup(Map<UUID, String> memberNames, Map<UUID, String> agentNames) {
            this.memberNames = memberNames;
            this.agentNames = agentNames;
        }

        /** Null for a null actor (nobody assigned). */
        ActorView view(Actor actor) {
            if (actor == null) {
                return null;
            }
            String name = actor.type() == ActorType.MEMBER
                    ? memberNames.getOrDefault(actor.id(), UNKNOWN_USER)
                    : agentNames.getOrDefault(actor.id(), UNKNOWN_AGENT);
            return new ActorView(actor.type(), actor.id(), name);
        }

        /** The author of a comment: {@code authorId} is null for the system. */
        ActorView author(ActorType type, UUID authorId) {
            return type == ActorType.SYSTEM
                    ? new ActorView(ActorType.SYSTEM, null, SYSTEM)
                    : view(new Actor(type, authorId));
        }
    }
}
