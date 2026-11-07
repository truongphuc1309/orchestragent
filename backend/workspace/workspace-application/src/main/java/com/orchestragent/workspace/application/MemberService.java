package com.orchestragent.workspace.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.application.Views.MemberView;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberPolicy;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listing, role changes and removal of members. Every method first checks that the caller belongs to the
 * workspace (a non-member gets "not found") and then applies {@link MemberPolicy}.
 */
@Service
public class MemberService {

    private final MemberRepository members;
    private final UserDirectory users;

    public MemberService(MemberRepository members, UserDirectory users) {
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(UUID actorId, UUID workspaceId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        requireMember(id, actorId);
        List<Member> all = members.findByWorkspace(id);
        Map<UUID, UserView> people = users.findByIds(all.stream().map(Member::userId).toList());
        return all.stream().map(member -> toView(member, people.get(member.userId()))).toList();
    }

    @Transactional
    public void changeRole(UUID actorId, UUID workspaceId, UUID targetUserId, WorkspaceRole newRole) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        Member actor = requireMember(id, actorId);
        Member target = members.find(id, targetUserId).orElseThrow(MemberService::memberNotFound);
        Role requested = Roles.toDomain(newRole);

        if (!MemberPolicy.canChangeRole(actor.role(), target.role(), requested)) {
            throw new ForbiddenException("member.role_change_forbidden", "You are not allowed to change this role");
        }
        if (target.role() == Role.OWNER && requested != Role.OWNER) {
            requireAnotherOwner(id);
        }
        target.changeRole(requested);
        members.save(target);
    }

    @Transactional
    public void remove(UUID actorId, UUID workspaceId, UUID targetUserId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        Member actor = requireMember(id, actorId);
        Member target = members.find(id, targetUserId).orElseThrow(MemberService::memberNotFound);

        boolean removingSelf = actorId.equals(targetUserId);
        if (!MemberPolicy.canRemove(actor.role(), target.role(), removingSelf)) {
            throw new ForbiddenException("member.remove_forbidden", "You are not allowed to remove this member");
        }
        if (target.role() == Role.OWNER) {
            requireAnotherOwner(id);
        }
        members.delete(target);
    }

    private Member requireMember(WorkspaceId workspaceId, UUID userId) {
        return members.find(workspaceId, userId).orElseThrow(WorkspaceService::notFound);
    }

    /** A workspace must always keep at least one owner, otherwise nobody could manage it any more. */
    private void requireAnotherOwner(WorkspaceId workspaceId) {
        if (members.countByRole(workspaceId, Role.OWNER) <= 1) {
            throw new ConflictException("workspace.last_owner", "A workspace must keep at least one owner");
        }
    }

    private static MemberView toView(Member member, UserView person) {
        String name = person == null ? "Unknown user" : person.name();
        String email = person == null ? "" : person.email();
        return new MemberView(member.userId(), name, email, Roles.toContract(member.role()), member.createdAt());
    }

    private static NotFoundException memberNotFound() {
        return new NotFoundException("member.not_found", "Member not found");
    }
}
