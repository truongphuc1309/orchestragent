package com.orchestragent.agent.application;

import com.orchestragent.agent.application.Views.AgentView;
import com.orchestragent.agent.application.Views.RuntimeSummary;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentPolicy;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, editing and archiving agents. Every method first checks that the caller belongs to the workspace
 * (a non-member gets "not found"). An agent works on the computer of the owner of its runtime, so a person can
 * only point an agent at a runtime of their own.
 */
@Service
public class AgentService {

    private final AgentRepository agents;
    private final RuntimeRepository runtimes;
    private final WorkspaceAccess access;
    private final UserDirectory users;
    private final Clock clock;

    AgentService(
            AgentRepository agents, RuntimeRepository runtimes, WorkspaceAccess access, UserDirectory users, Clock clock) {
        this.agents = agents;
        this.runtimes = runtimes;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** The editable part of an agent. Blank instructions and model are allowed (the model then is the tool's default). */
    public record AgentDetails(String name, String instructions, UUID runtimeId, String model) {
    }

    @Transactional
    public AgentView create(UUID userId, UUID workspaceId, AgentDetails details) {
        access.requireMember(workspaceId, userId);
        RuntimeId runtime = requireOwnRuntime(workspaceId, userId, details.runtimeId());
        Agent agent = Agent.create(
                workspaceId, details.name(), details.instructions(), runtime, details.model(), userId, clock.instant());
        requireNameFree(workspaceId, agent.name(), null);
        agents.save(agent);
        return view(agent);
    }

    @Transactional(readOnly = true)
    public List<AgentView> list(UUID userId, UUID workspaceId, boolean includeArchived) {
        access.requireMember(workspaceId, userId);
        return views(workspaceId, agents.findByWorkspace(workspaceId, includeArchived));
    }

    @Transactional(readOnly = true)
    public AgentView get(UUID userId, UUID workspaceId, UUID agentId) {
        access.requireMember(workspaceId, userId);
        return view(load(workspaceId, agentId));
    }

    /** Replaces the editable fields. Changing the runtime needs a runtime of the person making the change. */
    @Transactional
    public AgentView update(UUID userId, UUID workspaceId, UUID agentId, AgentDetails details) {
        Agent agent = loadManageable(userId, workspaceId, agentId);
        agent.requireActive();
        // Everything is checked before anything is changed, so a refused update leaves the agent as it was.
        String name = Agent.validName(details.name());
        Agent.validInstructions(details.instructions());
        Agent.validModel(details.model());
        requireNameFree(workspaceId, name, agent.id());
        RuntimeId runtime = agent.runtimeId().value().equals(details.runtimeId())
                ? agent.runtimeId()
                : requireOwnRuntime(workspaceId, userId, details.runtimeId());

        agent.changeRuntime(runtime);
        agent.rename(name);
        agent.changeInstructions(details.instructions());
        agent.changeModel(details.model());
        agents.save(agent);
        return view(agent);
    }

    @Transactional
    public AgentView archive(UUID userId, UUID workspaceId, UUID agentId) {
        Agent agent = loadManageable(userId, workspaceId, agentId);
        if (agent.archive(clock.instant())) {
            agents.save(agent);
        }
        return view(agent);
    }

    /** Brings an archived agent back, unless an active agent has taken its name meanwhile. */
    @Transactional
    public AgentView restore(UUID userId, UUID workspaceId, UUID agentId) {
        Agent agent = loadManageable(userId, workspaceId, agentId);
        if (agent.isArchived()) {
            requireNameFree(workspaceId, agent.name(), agent.id());
            agent.restore();
            agents.save(agent);
        }
        return view(agent);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private Agent loadManageable(UUID userId, UUID workspaceId, UUID agentId) {
        WorkspaceRole role = access.requireRole(workspaceId, userId, WorkspaceRole.MEMBER);
        Agent agent = load(workspaceId, agentId);
        if (!AgentPolicy.canManage(agent.createdBy().equals(userId), role.atLeast(WorkspaceRole.ADMIN))) {
            throw new ForbiddenException(
                    "agent.manage_forbidden", "Only the creator of an agent, an admin or an owner can change it");
        }
        return agent;
    }

    private Agent load(UUID workspaceId, UUID agentId) {
        return agents.find(workspaceId, new AgentId(agentId)).orElseThrow(AgentService::notFound);
    }

    private RuntimeId requireOwnRuntime(UUID workspaceId, UUID userId, UUID runtimeId) {
        if (runtimeId == null) {
            throw new ValidationException("agent.invalid_runtime", "Choose a runtime for the agent");
        }
        Runtime runtime = runtimes.find(workspaceId, new RuntimeId(runtimeId))
                .orElseThrow(() -> new ValidationException(
                        "agent.invalid_runtime", "The runtime does not exist in this workspace"));
        if (!runtime.ownerUserId().equals(userId)) {
            throw new ForbiddenException(
                    "agent.runtime_not_yours", "An agent can only use a runtime on your own computer");
        }
        return runtime.id();
    }

    /** Active agents of a workspace have distinct names (ignoring case); archived ones may repeat them. */
    private void requireNameFree(UUID workspaceId, String name, AgentId except) {
        if (agents.activeNameExists(workspaceId, name, except)) {
            throw new ConflictException("agent.name_taken", "Another agent already has this name");
        }
    }

    private AgentView view(Agent agent) {
        return views(agent.workspaceId(), List.of(agent)).getFirst();
    }

    private List<AgentView> views(UUID workspaceId, List<Agent> list) {
        Map<UUID, Runtime> byId = runtimes.findByWorkspace(workspaceId).stream()
                .collect(Collectors.toMap(runtime -> runtime.id().value(), Function.identity()));
        Map<UUID, UserView> creators = users.findByIds(list.stream().map(Agent::createdBy).distinct().toList());
        return list.stream()
                .map(agent -> toView(agent, byId.get(agent.runtimeId().value()), creators.get(agent.createdBy())))
                .toList();
    }

    private static AgentView toView(Agent agent, Runtime runtime, UserView creator) {
        return new AgentView(
                agent.id().value(),
                agent.workspaceId(),
                agent.name(),
                agent.instructions(),
                agent.model(),
                runtime == null
                        ? null
                        : new RuntimeSummary(runtime.id().value(), runtime.deviceName(), runtime.provider(), runtime.status()),
                agent.createdBy(),
                creator == null ? "Unknown user" : creator.name(),
                agent.archivedAt(),
                agent.createdAt());
    }

    static NotFoundException notFound() {
        return new NotFoundException("agent.not_found", "Agent not found");
    }
}
