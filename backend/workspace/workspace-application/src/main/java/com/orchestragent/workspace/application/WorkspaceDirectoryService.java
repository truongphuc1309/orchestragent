package com.orchestragent.workspace.application;

import com.orchestragent.workspace.contract.WorkspaceDirectory;
import com.orchestragent.workspace.contract.WorkspaceRef;
import com.orchestragent.workspace.domain.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceDirectoryService implements WorkspaceDirectory {

    private final WorkspaceRepository workspaces;

    WorkspaceDirectoryService(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceRef> listForUser(UUID userId) {
        return workspaces.findByMember(userId).stream()
                .map(workspace -> new WorkspaceRef(workspace.id().value(), workspace.name(), workspace.slug()))
                .toList();
    }
}
