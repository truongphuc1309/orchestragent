import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { archiveAgent, createAgent, listAgents, listRuntimes, restoreAgent, updateAgent } from './api'
import type { AgentForm } from './schemas'

/** Every key contains the workspace ID, so data of two workspaces never shares a cache entry. */
export const agentKeys = {
  agents: (workspaceId: string) => ['workspaces', workspaceId, 'agents'] as const,
  list: (workspaceId: string, includeArchived: boolean) =>
    [...agentKeys.agents(workspaceId), 'list', includeArchived] as const,
  runtimes: (workspaceId: string) => ['workspaces', workspaceId, 'runtimes'] as const,
}

/** How often the runtime list asks again: the server marks a silent computer offline within two minutes. */
const RUNTIME_REFRESH_MS = 15_000

export function useRuntimes(workspaceId: string) {
  return useQuery({
    queryKey: agentKeys.runtimes(workspaceId),
    queryFn: ({ signal }) => listRuntimes(workspaceId, signal),
    refetchInterval: RUNTIME_REFRESH_MS,
  })
}

export function useAgents(workspaceId: string, includeArchived = false) {
  return useQuery({
    queryKey: agentKeys.list(workspaceId, includeArchived),
    queryFn: ({ signal }) => listAgents(workspaceId, includeArchived, signal),
  })
}

/** Agents show up in issue assignee pickers and on issues, so a change refreshes both. */
function useRefreshAgents(workspaceId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: agentKeys.agents(workspaceId) }),
      queryClient.invalidateQueries({ queryKey: ['workspaces', workspaceId, 'issues'] }),
    ])
}

export function useCreateAgent(workspaceId: string) {
  const refresh = useRefreshAgents(workspaceId)
  return useMutation({ mutationFn: (form: AgentForm) => createAgent(workspaceId, form), onSuccess: refresh })
}

export function useUpdateAgent(workspaceId: string) {
  const refresh = useRefreshAgents(workspaceId)
  return useMutation({
    mutationFn: ({ agentId, form }: { agentId: string; form: AgentForm }) => updateAgent(workspaceId, agentId, form),
    onSuccess: refresh,
  })
}

export function useArchiveAgent(workspaceId: string) {
  const refresh = useRefreshAgents(workspaceId)
  return useMutation({ mutationFn: (agentId: string) => archiveAgent(workspaceId, agentId), onSuccess: refresh })
}

export function useRestoreAgent(workspaceId: string) {
  const refresh = useRefreshAgents(workspaceId)
  return useMutation({ mutationFn: (agentId: string) => restoreAgent(workspaceId, agentId), onSuccess: refresh })
}
