package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import com.orchestragent.workspace.domain.Email;
import com.orchestragent.workspace.domain.Invitation;
import com.orchestragent.workspace.domain.InvitationId;
import com.orchestragent.workspace.domain.InvitationRepository;
import com.orchestragent.workspace.domain.InvitationStatus;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class InvitationRepositoryAdapter implements InvitationRepository {

    private static final String PENDING = code(InvitationStatus.PENDING);

    private final InvitationJpaRepository jpa;

    InvitationRepositoryAdapter(InvitationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Invitation invitation) {
        jpa.findById(invitation.id().value()).ifPresentOrElse(
                existing -> existing.updateStatus(code(invitation.status())),
                () -> jpa.save(new InvitationEntity(
                        invitation.id().value(),
                        invitation.workspaceId().value(),
                        invitation.email().value(),
                        MemberRepositoryAdapter.code(invitation.role()),
                        code(invitation.status()),
                        invitation.invitedBy(),
                        invitation.createdAt())));
    }

    @Override
    public Optional<Invitation> findById(InvitationId id) {
        return jpa.findById(id.value()).map(InvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> findPending(WorkspaceId workspaceId, Email email) {
        return jpa.findByWorkspaceIdAndEmailAndStatus(workspaceId.value(), email.value(), PENDING)
                .map(InvitationRepositoryAdapter::toDomain);
    }

    @Override
    public List<Invitation> findPendingByWorkspace(WorkspaceId workspaceId) {
        return jpa.findByWorkspaceIdAndStatusOrderByCreatedAtDescIdDesc(workspaceId.value(), PENDING).stream()
                .map(InvitationRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Invitation> findPendingByEmail(Email email) {
        return jpa.findByEmailAndStatusOrderByCreatedAtDescIdDesc(email.value(), PENDING).stream()
                .map(InvitationRepositoryAdapter::toDomain)
                .toList();
    }

    private static String code(InvitationStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static Invitation toDomain(InvitationEntity entity) {
        return new Invitation(
                new InvitationId(entity.getId()),
                new WorkspaceId(entity.getWorkspaceId()),
                Email.of(entity.getEmail()),
                MemberRepositoryAdapter.role(entity.getRole()),
                InvitationStatus.valueOf(entity.getStatus().toUpperCase(Locale.ROOT)),
                entity.getInvitedBy(),
                entity.getCreatedAt());
    }
}
