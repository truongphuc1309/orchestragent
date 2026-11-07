package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberId;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;

    MemberRepositoryAdapter(MemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Member member) {
        jpa.findById(member.id().value()).ifPresentOrElse(
                existing -> existing.updateRole(code(member.role())),
                () -> jpa.save(new MemberEntity(
                        member.id().value(),
                        member.workspaceId().value(),
                        member.userId(),
                        code(member.role()),
                        member.createdAt())));
    }

    @Override
    public void delete(Member member) {
        jpa.deleteById(member.id().value());
    }

    @Override
    public Optional<Member> find(WorkspaceId workspaceId, UUID userId) {
        return jpa.findByWorkspaceIdAndUserId(workspaceId.value(), userId).map(MemberRepositoryAdapter::toDomain);
    }

    @Override
    public List<Member> findByWorkspace(WorkspaceId workspaceId) {
        return jpa.findByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId.value()).stream()
                .map(MemberRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Member> findByUser(UUID userId) {
        return jpa.findByUserId(userId).stream().map(MemberRepositoryAdapter::toDomain).toList();
    }

    @Override
    public long countByRole(WorkspaceId workspaceId, Role role) {
        return jpa.countByWorkspaceIdAndRole(workspaceId.value(), code(role));
    }

    static String code(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    static Role role(String code) {
        return Role.valueOf(code.toUpperCase(Locale.ROOT));
    }

    private static Member toDomain(MemberEntity entity) {
        return new Member(
                new MemberId(entity.getId()),
                new WorkspaceId(entity.getWorkspaceId()),
                entity.getUserId(),
                role(entity.getRole()),
                entity.getCreatedAt());
    }
}
