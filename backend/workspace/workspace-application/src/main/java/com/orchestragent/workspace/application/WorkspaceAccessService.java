package com.orchestragent.workspace.application;

import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceAccessService implements WorkspaceAccess {

    private final MemberRepository members;

    WorkspaceAccessService(MemberRepository members) {
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireMember(UUID workspaceId, UUID userId) {
        memberOrNotFound(workspaceId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole requireRole(UUID workspaceId, UUID userId, WorkspaceRole minimum) {
        WorkspaceRole actual = Roles.toContract(memberOrNotFound(workspaceId, userId).role());
        if (!actual.atLeast(minimum)) {
            throw new ForbiddenException("workspace.role_required", "This action needs the " + minimum + " role or higher");
        }
        return actual;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UUID workspaceId, UUID userId) {
        return members.find(new WorkspaceId(workspaceId), userId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listMemberIds(UUID workspaceId) {
        return members.findByWorkspace(new WorkspaceId(workspaceId)).stream().map(Member::userId).toList();
    }

    private Member memberOrNotFound(UUID workspaceId, UUID userId) {
        return members.find(new WorkspaceId(workspaceId), userId).orElseThrow(WorkspaceService::notFound);
    }
}
