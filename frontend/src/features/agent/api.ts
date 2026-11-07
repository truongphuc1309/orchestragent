import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { agentSchema, runtimeSchema, type Agent, type AgentForm, type Runtime } from './schemas'

const base = (workspaceId: string) => `/api/workspaces/${workspaceId}`

export function listRuntimes(workspaceId: string, signal?: AbortSignal): Promise<Runtime[]> {
  return apiRequest(`${base(workspaceId)}/runtimes`, { schema: z.array(runtimeSchema), signal })
}

/** Active agents only, unless {@code includeArchived} is set. */
export function listAgents(workspaceId: string, includeArchived: boolean, signal?: AbortSignal): Promise<Agent[]> {
  const query = includeArchived ? '?includeArchived=true' : ''
  return apiRequest(`${base(workspaceId)}/agents${query}`, { schema: z.array(agentSchema), signal })
}

function requestBody(form: AgentForm) {
  return { name: form.name, instructions: form.instructions, runtimeId: form.runtimeId, model: form.model }
}

export function createAgent(workspaceId: string, form: AgentForm): Promise<Agent> {
  return apiRequest(`${base(workspaceId)}/agents`, { method: 'POST', body: requestBody(form), schema: agentSchema })
}

/** Replaces the editable fields of the agent. */
export function updateAgent(workspaceId: string, agentId: string, form: AgentForm): Promise<Agent> {
  return apiRequest(`${base(workspaceId)}/agents/${agentId}`, {
    method: 'PUT',
    body: requestBody(form),
    schema: agentSchema,
  })
}

export function archiveAgent(workspaceId: string, agentId: string): Promise<Agent> {
  return apiRequest(`${base(workspaceId)}/agents/${agentId}/archive`, { method: 'POST', schema: agentSchema })
}

export function restoreAgent(workspaceId: string, agentId: string): Promise<Agent> {
  return apiRequest(`${base(workspaceId)}/agents/${agentId}/restore`, { method: 'POST', schema: agentSchema })
}
