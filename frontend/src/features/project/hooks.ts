import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { archiveProject, createProject, getProjectByKey, listProjects, restoreProject, updateProject } from './api'
import type { ProjectForm } from './schemas'

/** Every key contains the workspace ID, so projects of two workspaces never share a cache entry. */
export const projectKeys = {
  all: (workspaceId: string) => ['workspaces', workspaceId, 'projects'] as const,
  list: (workspaceId: string, includeArchived: boolean) =>
    [...projectKeys.all(workspaceId), 'list', includeArchived] as const,
  byKey: (workspaceId: string, key: string) => [...projectKeys.all(workspaceId), 'key', key] as const,
}

export function useProjects(workspaceId: string, includeArchived = false) {
  return useQuery({
    queryKey: projectKeys.list(workspaceId, includeArchived),
    queryFn: ({ signal }) => listProjects(workspaceId, includeArchived, signal),
  })
}

export function useProjectByKey(workspaceId: string, key: string) {
  return useQuery({
    queryKey: projectKeys.byKey(workspaceId, key),
    queryFn: ({ signal }) => getProjectByKey(workspaceId, key, signal),
    // A missing project will not appear by asking again.
    retry: false,
  })
}

function useRefreshProjects(workspaceId: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: projectKeys.all(workspaceId) })
}

export function useCreateProject(workspaceId: string) {
  const refresh = useRefreshProjects(workspaceId)
  return useMutation({ mutationFn: (form: ProjectForm) => createProject(workspaceId, form), onSuccess: refresh })
}

export function useUpdateProject(workspaceId: string) {
  const refresh = useRefreshProjects(workspaceId)
  return useMutation({
    mutationFn: ({ projectId, changes }: { projectId: string; changes: { name: string; description: string } }) =>
      updateProject(workspaceId, projectId, changes),
    onSuccess: refresh,
  })
}

export function useArchiveProject(workspaceId: string) {
  const refresh = useRefreshProjects(workspaceId)
  return useMutation({ mutationFn: (projectId: string) => archiveProject(workspaceId, projectId), onSuccess: refresh })
}

export function useRestoreProject(workspaceId: string) {
  const refresh = useRefreshProjects(workspaceId)
  return useMutation({ mutationFn: (projectId: string) => restoreProject(workspaceId, projectId), onSuccess: refresh })
}
